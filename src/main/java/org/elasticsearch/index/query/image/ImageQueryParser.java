package org.elasticsearch.index.query.image;


import net.semanticmetadata.lire.imageanalysis.LireFeature;
import net.semanticmetadata.lire.indexing.hashing.BitSampling;
import net.semanticmetadata.lire.indexing.hashing.LocalitySensitiveHashing;
import net.semanticmetadata.lire.utils.ImageUtils;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.elasticsearch.ElasticsearchImageProcessException;
import org.elasticsearch.common.io.stream.BytesStreamInput;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.mapper.image.FeatureEnum;
import org.elasticsearch.index.mapper.image.HashEnum;
import org.elasticsearch.index.mapper.image.ImageMapper;
import org.elasticsearch.index.query.QueryParseContext;
import org.elasticsearch.index.query.QueryParser;
import org.elasticsearch.index.query.QueryParsingException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;

public class ImageQueryParser implements QueryParser {

    public static final String NAME = "image";


    @Override
    public String[] names() {
        return new String[] {NAME};
    }

    @Override
    public Query parse(QueryParseContext parseContext) throws IOException, QueryParsingException {
        XContentParser parser = parseContext.parser();

        XContentParser.Token token = parser.nextToken();
        if (token != XContentParser.Token.FIELD_NAME) {
            throw new QueryParsingException(parseContext.index(), "[image] query malformed, no field");
        }


        String fieldName = parser.currentName();
        FeatureEnum featureEnum = null;
        byte[] image = null;
        HashEnum hashEnum = null;
        float boost = 1.0f;
        int limit = -1;


        token = parser.nextToken();
        if (token == XContentParser.Token.START_OBJECT) {
            String currentFieldName = null;
            while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
                if (token == XContentParser.Token.FIELD_NAME) {
                    currentFieldName = parser.currentName();
                } else {
                    if ("feature".equals(currentFieldName)) {
                        featureEnum = FeatureEnum.getByName(parser.text());
                    } else if ("image".equals(currentFieldName)) {
                        image = parser.binaryValue();
                    } else if ("hash".equals(currentFieldName)) {
                        hashEnum = HashEnum.getByName(parser.text());
                    } else if ("boost".equals(currentFieldName)) {
                        boost = parser.floatValue();
                    } else if ("limit".equals(currentFieldName)) {
                        limit = parser.intValue();
                    } else {
                        throw new QueryParsingException(parseContext.index(), "[image] query does not support [" + currentFieldName + "]");
                    }
                }
            }
            parser.nextToken();
        }

        if (featureEnum == null) {
            throw new QueryParsingException(parseContext.index(), "No feature specified for image query");
        }
        if (image == null) {
            throw new QueryParsingException(parseContext.index(), "No image specified for image query");
        }

        String luceneFieldName = fieldName + "." + featureEnum.name();
        LireFeature feature;
        try {
            feature = featureEnum.getFeatureClass().newInstance();
            BufferedImage img = ImageIO.read(new BytesStreamInput(image, false));
            if (Math.max(img.getHeight(), img.getWidth()) > ImageMapper.MAX_IMAGE_DIMENSION) {
                img = ImageUtils.scaleImage(img, ImageMapper.MAX_IMAGE_DIMENSION);
            }
            feature.extract(img);
        } catch (Exception e) {
            throw new ElasticsearchImageProcessException("Failed to parse image", e);
        }

        if (hashEnum == null) {  // no hash, need to scan all documents
            return new ImageQuery(luceneFieldName, feature, boost);
        } else {  // query by hash first
            int[] hash = null;
            if (hashEnum.equals(HashEnum.BIT_SAMPLING)) {
                hash = BitSampling.generateHashes(feature.getDoubleHistogram());
            } else if (hashEnum.equals(HashEnum.LSH)) {
                hash = LocalitySensitiveHashing.generateHashes(feature.getDoubleHistogram());
            }
            String hashFieldName = luceneFieldName + "." + ImageMapper.HASH + "." + hashEnum.name();

            if (limit > 0) {  // has max result limit, use ImageHashLimitQuery
                return new ImageHashLimitQuery(hashFieldName, hash, limit, luceneFieldName, feature, boost);
            } else {  // no max result limit, use ImageHashQuery
                BooleanQuery query = new BooleanQuery(true);
                ImageScoreCache imageScoreCache = new ImageScoreCache();

                for (int h : hash) {
                    query.add(new BooleanClause(new ImageHashQuery(new Term(hashFieldName, Integer.toString(h)), luceneFieldName, feature, imageScoreCache, boost), BooleanClause.Occur.SHOULD));
                }
                return query;
            }

        }
    }
}
