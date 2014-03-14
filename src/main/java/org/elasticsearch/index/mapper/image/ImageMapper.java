package org.elasticsearch.index.mapper.image;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
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
import org.elasticsearch.common.collect.MapMaker;
import org.elasticsearch.common.collect.Maps;
import org.elasticsearch.common.hppc.cursors.ObjectObjectCursor;
import org.elasticsearch.common.io.stream.BytesStreamInput;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.mapper.*;
import org.elasticsearch.threadpool.ThreadPool;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

import static org.elasticsearch.index.mapper.MapperBuilders.binaryField;
import static org.elasticsearch.index.mapper.MapperBuilders.stringField;



public class ImageMapper implements Mapper {

    private static ESLogger logger = ESLoggerFactory.getLogger(ImageMapper.class.getName());

    public static final int MAX_IMAGE_DIMENSION = 1024;

    public static final String CONTENT_TYPE = "image";

    public static final String HASH = "hash";

    public static final String FEATURE = "feature";
    public static final String METADATA = "metadata";

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

        private ThreadPool threadPool;

        private Map<FeatureEnum, Map<String, Object>> features = Maps.newHashMap();

        private Map<String, Mapper.Builder> metadataBuilders = Maps.newHashMap();

        public Builder(String name, ThreadPool threadPool) {
            super(name);
            this.threadPool = threadPool;
            this.builder = this;
        }

        public Builder addFeature(FeatureEnum featureEnum, Map<String, Object> featureMap) {
            this.features.put(featureEnum, featureMap);
            return this;
        }

        public Builder addMetadata(String metadata, Mapper.Builder metadataBuilder) {
            this.metadataBuilders.put(metadata, metadataBuilder);
            return this;
        }

        @Override
        public ImageMapper build(BuilderContext context) {
            Map<String, Mapper> featureMappers = Maps.newHashMap();
            Map<String, Mapper> hashMappers = Maps.newHashMap();
            Map<String, Mapper> metadataMappers = Maps.newHashMap();

            context.path().add(name);
            // add feature and hash mappers
            for (FeatureEnum featureEnum : features.keySet()) {
                Map<String, Object> featureMap = features.get(featureEnum);
                String featureName = featureEnum.name();

                // add feature mapper
                featureMappers.put(featureName, binaryField(featureName).store(true).includeInAll(false).index(false).build(context));


                // add hash mapper if hash is required
                if (featureMap.containsKey(HASH)){
                    List<String> hashes = (List<String>) featureMap.get(HASH);
                    for (String h : hashes) {
                        String hashFieldName = featureName + "." + HASH + "." + h;
                        hashMappers.put(hashFieldName, stringField(hashFieldName).store(true).includeInAll(false).index(true).build(context));
                    }
                }
            }

            // add metadata mappers
            context.path().add(METADATA);
            for (Map.Entry<String, Mapper.Builder> entry : metadataBuilders.entrySet()){
                String metadataName = entry.getKey();
                Mapper.Builder metadataBuilder = entry.getValue();
                metadataMappers.put(metadataName, metadataBuilder.build(context));
            }
            context.path().remove();  // remove METADATA
            context.path().remove();  // remove name

            return new ImageMapper(name, threadPool, features, featureMappers, hashMappers, metadataMappers);
        }
    }

    public static class TypeParser implements Mapper.TypeParser {
        private ThreadPool threadPool;

        public TypeParser(ThreadPool threadPool) {
            this.threadPool = threadPool;
        }

        @SuppressWarnings({"unchecked"})
        @Override
        public Mapper.Builder parse(String name, Map<String, Object> node, ParserContext parserContext) throws MapperParsingException {
            ImageMapper.Builder builder = new ImageMapper.Builder(name, threadPool);
            Map<String, Object> features = Maps.newHashMap();
            Map<String, Object> metadatas = Maps.newHashMap();

            for (Map.Entry<String, Object> entry : node.entrySet()) {
                String fieldName = entry.getKey();
                Object fieldNode = entry.getValue();

                if (FEATURE.equals(fieldName)) {
                    features = (Map<String, Object>) fieldNode;
                } else if (METADATA.equals(fieldName)) {
                    metadatas = (Map<String, Object>) fieldNode;
                }
            }

            if (features == null || features.isEmpty()) {
                throw new ElasticsearchIllegalArgumentException("Feature not found");
            }

            // process features
            for (Map.Entry<String, Object> entry : features.entrySet()) {
                String feature = entry.getKey();
                Map<String, Object> featureMap = (Map<String, Object>) entry.getValue();

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
                builder.addFeature(featureEnum, featureMap);
            }


            // process metadata
            for (Map.Entry<String, Object> entry : metadatas.entrySet()) {
                String metadataName = entry.getKey();
                Map<String, Object> metadataMap = (Map<String, Object>) entry.getValue();
                String fieldType = (String) metadataMap.get("type");
                builder.addMetadata(metadataName, parserContext.typeParser(fieldType).parse(metadataName, metadataMap, parserContext));
            }

            return builder;
        }
    }

    private final String name;

    private final ThreadPool threadPool;

    private volatile ImmutableOpenMap<FeatureEnum, Map<String, Object>> features = ImmutableOpenMap.of();

    private volatile ImmutableOpenMap<String, Mapper> featureMappers = ImmutableOpenMap.of();

    private volatile ImmutableOpenMap<String, Mapper> hashMappers = ImmutableOpenMap.of();

    private volatile ImmutableOpenMap<String, Mapper> metadataMappers = ImmutableOpenMap.of();


    public ImageMapper(String name, ThreadPool threadPool, Map<FeatureEnum, Map<String, Object>> features, Map<String, Mapper> featureMappers,
                       Map<String, Mapper> hashMappers, Map<String, Mapper> metadataMappers) {
        this.name = name;
        this.threadPool = threadPool;
        if (features != null) {
            this.features = ImmutableOpenMap.builder(this.features).putAll(features).build();
        }
        if (featureMappers != null) {
            this.featureMappers = ImmutableOpenMap.builder(this.featureMappers).putAll(featureMappers).build();
        }
        if (hashMappers != null) {
            this.hashMappers = ImmutableOpenMap.builder(this.hashMappers).putAll(hashMappers).build();
        }
        if (metadataMappers != null) {
            this.metadataMappers = ImmutableOpenMap.builder(this.metadataMappers).putAll(metadataMappers).build();
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
        final BufferedImage finalImg = img;



        final Map<FeatureEnum, LireFeature> featureExtractMap = new MapMaker().makeMap();

        // have multiple features, use ThreadPool to process each feature
        if (features.size() > 1) {
            final CountDownLatch latch = new CountDownLatch(features.size());
            Executor executor = threadPool.generic();

            for (ObjectObjectCursor<FeatureEnum, Map<String, Object>> cursor : features) {
                final FeatureEnum featureEnum = cursor.key;
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            LireFeature lireFeature = featureEnum.getFeatureClass().newInstance();
                            lireFeature.extract(finalImg);
                            featureExtractMap.put(featureEnum, lireFeature);
                        } catch (Throwable e){
                            logger.error("Failed to extract feature from image", e);
                        } finally {
                            latch.countDown();
                        }
                    }
                });
            }
            try {
                latch.await();
            } catch (InterruptedException e) {
                logger.debug("Interrupted extract feature from image", e);
                Thread.currentThread().interrupt();
            }
        }


        for (ObjectObjectCursor<FeatureEnum, Map<String, Object>> cursor : features) {
            FeatureEnum featureEnum = cursor.key;
            Map<String, Object> featureMap = cursor.value;

            try {
                LireFeature lireFeature;
                if (featureExtractMap.containsKey(featureEnum)) {   // already processed
                    lireFeature = featureExtractMap.get(featureEnum);
                } else {
                    lireFeature = featureEnum.getFeatureClass().newInstance();
                    lireFeature.extract(img);
                }
                byte[] parsedContent = lireFeature.getByteArrayRepresentation();

                // todo: BinaryFieldMapper doesn't support externalValue, https://github.com/elasticsearch/elasticsearch/pull/4986
                String featureFieldName = name + "." + featureEnum.name();
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

                        String mapperName = featureEnum.name() + "." + HASH + "." + h;
                        Mapper hashMapper = hashMappers.get(mapperName);
                        context.externalValue(SerializationUtils.arrayToString(hashVals));
                        hashMapper.parse(context);
                    }
                }
            } catch (Exception e) {
                throw new ElasticsearchImageProcessException("Failed to index feature " + featureEnum.name(), e);
            }
        }

        // process metadata if required
        if (!metadataMappers.isEmpty()) {
            try {
                Metadata metadata = ImageMetadataReader.readMetadata(new BufferedInputStream(new BytesStreamInput(content, false)), false);
                for (Directory directory : metadata.getDirectories()) {
                    for (Tag tag : directory.getTags()) {
                        String metadataName = tag.getDirectoryName().toLowerCase().replaceAll("\\s+", "_") + "." +
                                tag.getTagName().toLowerCase().replaceAll("\\s+", "_");
                        if (metadataMappers.containsKey(metadataName)) {
                            Mapper mapper = metadataMappers.get(metadataName);
                            context.externalValue(tag.getDescription());
                            mapper.parse(context);
                        }
                    }
                }
            } catch (ImageProcessingException e) {
                logger.warn("Failed to extract metadata from image", e);
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
        for (ObjectObjectCursor<String, Mapper> cursor : metadataMappers) {
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

        builder.startObject(FEATURE);
        for (ObjectObjectCursor<FeatureEnum, Map<String, Object>> cursor : features) {
            builder.field(cursor.key.name(), cursor.value);
        }
        builder.endObject();

        builder.startObject(METADATA);
        for (ObjectObjectCursor<String, Mapper> cursor : metadataMappers) {
            cursor.value.toXContent(builder, params);
        }
        builder.endObject();

        builder.endObject();
        return builder;
    }
}
