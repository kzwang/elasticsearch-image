package org.elasticsearch.index.mapper.image;

import net.semanticmetadata.lire.imageanalysis.LireFeature;
import net.semanticmetadata.lire.indexing.hashing.BitSampling;
import net.semanticmetadata.lire.indexing.hashing.LocalitySensitiveHashing;
import net.semanticmetadata.lire.utils.ImageUtils;
import net.semanticmetadata.lire.utils.SerializationUtils;
import org.apache.lucene.document.StoredField;
import org.elasticsearch.ElasticsearchIllegalArgumentException;
import org.elasticsearch.ElasticsearchImageProcessException;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.elasticsearch.common.collect.Lists;
import org.elasticsearch.common.collect.Maps;
import org.elasticsearch.common.hppc.cursors.ObjectObjectCursor;
import org.elasticsearch.common.io.stream.BytesStreamInput;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.mapper.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.elasticsearch.index.mapper.MapperBuilders.binaryField;
import static org.elasticsearch.index.mapper.MapperBuilders.stringField;



public class ImageMapper implements Mapper {

    private static ESLogger logger = ESLoggerFactory.getLogger(ImageMapper.class.getName());

    public static final int MAX_IMAGE_DIMENSION = 1024;

    public static final String CONTENT_TYPE = "image";

    public static final String HASH = "hash";

    public static final String BIT_SAMPLING_FILE = "/hash/LshBitSampling.obj";
    public static final String LSH_HASH_FILE = "/hash/lshHashFunctions.obj";

    static {
        try {
            BitSampling.readHashFunctions(ImageMapper.class.getResourceAsStream(BIT_SAMPLING_FILE));
            LocalitySensitiveHashing.readHashFunctions(ImageMapper.class.getResourceAsStream(LSH_HASH_FILE));
        } catch (IOException e) {
            logger.error("Failed to initialize hash function", e);
        }
    }


    public static class Builder extends Mapper.Builder<Builder, ImageMapper> {

        private Map<String, Map<String, Object>> features = Maps.newHashMap();

        public Builder(String name) {
            super(name);
            this.builder = this;
        }

        public Builder addFeature(String featureName, Map<String, Object> featureMap) {
            this.features.put(featureName, featureMap);
            return this;
        }

        @Override
        public ImageMapper build(BuilderContext context) {
            Map<String, Mapper> featureMappers = Maps.newHashMap();
            Map<String, Mapper> hashMappers = Maps.newHashMap();
            for (String feature : features.keySet()) {
                Map<String, Object> featureMap = features.get(feature);

                // add feature mapper
                String featureFieldName = name + "." + feature;
                featureMappers.put(feature, binaryField(featureFieldName).store(true).includeInAll(false).index(false).build(context));


                // add hash mapper if hash is required
                if (featureMap.containsKey(HASH)){
                    List<String> hashes = (List<String>) featureMap.get(HASH);
                    for (String h : hashes) {
                        String hashFieldName = name + "." + feature + "." + HASH + "." + h;
                        String mapperName = feature + "." + h;
                        hashMappers.put(mapperName, stringField(hashFieldName).store(true).includeInAll(false).index(true).build(context));
                    }
                }
            }
            return new ImageMapper(name, features, featureMappers, hashMappers);
        }
    }

    public static class TypeParser implements Mapper.TypeParser {
        @SuppressWarnings({"unchecked"})
        @Override
        public Mapper.Builder parse(String name, Map<String, Object> node, ParserContext parserContext) throws MapperParsingException {
            ImageMapper.Builder builder = new ImageMapper.Builder(name);
            Map<String, Object> features = null;

            for (Map.Entry<String, Object> entry : node.entrySet()) {
                String fieldName = entry.getKey();
                Object fieldNode = entry.getValue();

                if ("feature".equals(fieldName)) {
                    features = (Map<String, Object>) fieldNode;
                }
            }

            if (features == null || features.isEmpty()) {
                throw new ElasticsearchIllegalArgumentException("Feature not found");
            }

            // process features
            for (String feature : features.keySet()) {
                Map<String, Object> featureMap = (Map<String, Object>) features.get(feature);

                // process hash for each feature
                if (featureMap.containsKey(HASH)) {
                    Object hashVal = featureMap.get(HASH);
                    List<String> hashes = Lists.newArrayList();
                    if (hashVal instanceof List) {
                        for (String h : (List<String>)hashVal) {
                            hashes.add(HashEnum.valueOf(h).name());
                        }
                    } else if (hashVal instanceof String) {
                        hashes.add(HashEnum.valueOf((String) hashVal).name());
                    } else {
                        throw new ElasticsearchIllegalArgumentException("Malformed hash value");
                    }
                    featureMap.put(HASH, hashes);
                }

                FeatureEnum featureEnum = FeatureEnum.getByName(feature);
                builder.addFeature(featureEnum.name(), featureMap);
            }
            return builder;
        }
    }

    private final String name;

    private volatile ImmutableOpenMap<String, Map<String, Object>> features = ImmutableOpenMap.of();

    private volatile ImmutableOpenMap<String, Mapper> featureMappers = ImmutableOpenMap.of();

    private volatile ImmutableOpenMap<String, Mapper> hashMappers = ImmutableOpenMap.of();


    public ImageMapper(String name, Map<String, Map<String, Object>> features, Map<String, Mapper> featureMappers, Map<String, Mapper> hashMappers) {
        this.name = name;
        if (features != null) {
            this.features = ImmutableOpenMap.builder(this.features).putAll(features).build();
        }
        if (featureMappers != null) {
            this.featureMappers = ImmutableOpenMap.builder(this.featureMappers).putAll(featureMappers).build();
        }
        if (hashMappers != null) {
            this.hashMappers = ImmutableOpenMap.builder(this.hashMappers).putAll(hashMappers).build();
        }
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void parse(ParseContext context) throws IOException {
        byte[] content = null;

        XContentParser parser = context.parser();
        XContentParser.Token token = parser.currentToken();
        if (token == XContentParser.Token.VALUE_STRING) {
            content = parser.binaryValue();
        }

        if (content == null) {
            throw new MapperParsingException("No content is provided.");
        }

        BufferedImage img = ImageIO.read(new BytesStreamInput(content, false));
        if (Math.max(img.getHeight(), img.getWidth()) > MAX_IMAGE_DIMENSION) {
            img = ImageUtils.scaleImage(img, MAX_IMAGE_DIMENSION);
        }

        for (ObjectObjectCursor<String, Map<String, Object>> cursor : features) {
            FeatureEnum featureEnum = FeatureEnum.getByName(cursor.key);
            Map<String, Object> featureMap = cursor.value;

            try {
                LireFeature lireFeature = featureEnum.getFeatureClass().newInstance();
                lireFeature.extract(img);
                byte[] parsedContent = lireFeature.getByteArrayRepresentation();
                String featureFieldName = name + "." + featureEnum.name();

                // todo: BinaryFieldMapper doesn't support externalValue, https://github.com/elasticsearch/elasticsearch/pull/4986
                StoredField featureField = new StoredField(featureFieldName, parsedContent);
                context.doc().add(featureField);

                // add hash if required
                if (featureMap.containsKey(HASH)) {
                    List<String> hashes = (List<String>) featureMap.get(HASH);
                    for (String h : hashes) {
                        HashEnum hashEnum = HashEnum.valueOf(h);
                        int[] hashVals = null;
                        if (hashEnum.equals(HashEnum.BIT_SAMPLING)) {
                            hashVals = BitSampling.generateHashes(lireFeature.getDoubleHistogram());
                        } else if (hashEnum.equals(HashEnum.LSH)) {
                            hashVals = LocalitySensitiveHashing.generateHashes(lireFeature.getDoubleHistogram());
                        }

                        String mapperName = featureEnum.name() + "." + h;
                        Mapper hashMapper = hashMappers.get(mapperName);
                        context.externalValue(SerializationUtils.arrayToString(hashVals));
                        hashMapper.parse(context);
                    }
                }
            } catch (Exception e) {
                throw new ElasticsearchImageProcessException("Failed to index feature " + featureEnum.name(), e);
            }
        }

    }

    @Override
    public void merge(Mapper mergeWith, MergeContext mergeContext) throws MergeMappingException {
    }

    @Override
    public void traverse(FieldMapperListener fieldMapperListener) {
        for (ObjectObjectCursor<String, Mapper> cursor : featureMappers) {
            cursor.value.traverse(fieldMapperListener);
        }
        for (ObjectObjectCursor<String, Mapper> cursor : hashMappers) {
            cursor.value.traverse(fieldMapperListener);
        }
    }

    @Override
    public void traverse(ObjectMapperListener objectMapperListener) {
    }


    @Override
    public void close() {
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(name);

        builder.field("type", CONTENT_TYPE);

        builder.startObject("feature");
        for (ObjectObjectCursor<String, Map<String, Object>> cursor : features) {
            builder.field(cursor.key, cursor.value);
        }
        builder.endObject();

        builder.endObject();
        return builder;
    }
}
