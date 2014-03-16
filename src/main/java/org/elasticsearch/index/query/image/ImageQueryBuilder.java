package org.elasticsearch.index.query.image;


import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.BaseQueryBuilder;
import org.elasticsearch.index.query.BoostableQueryBuilder;

import java.io.IOException;

public class ImageQueryBuilder extends BaseQueryBuilder implements BoostableQueryBuilder<ImageQueryBuilder> {

    private final String fieldName;

    private String feature;

    private byte[] image;

    private String hash;

    private float boost = -1;

    private int limit = -1;

    private String lookupIndex;

    private String lookupType;

    private String lookupId;

    private String lookupRouting;

    private String lookupPath;

    public ImageQueryBuilder(String fieldName) {
        this.fieldName = fieldName;
    }

    public ImageQueryBuilder feature(String feature) {
        this.feature = feature;
        return this;
    }

    public ImageQueryBuilder image(byte[] image) {
        this.image = image;
        return this;
    }

    public ImageQueryBuilder hash(String hash) {
        this.hash = hash;
        return this;
    }

    public ImageQueryBuilder limit(int limit) {
        this.limit = limit;
        return this;
    }

    public ImageQueryBuilder lookupIndex(String lookupIndex) {
        this.lookupIndex = lookupIndex;
        return this;
    }

    public ImageQueryBuilder lookupType(String lookupType) {
        this.lookupType = lookupType;
        return this;
    }

    public ImageQueryBuilder lookupId(String lookupId) {
        this.lookupId = lookupId;
        return this;
    }

    public ImageQueryBuilder lookupPath(String lookupPath) {
        this.lookupPath = lookupPath;
        return this;
    }

    public ImageQueryBuilder lookupRouting(String lookupRouting) {
        this.lookupRouting = lookupRouting;
        return this;
    }

    @Override
    public ImageQueryBuilder boost(float boost) {
        this.boost = boost;
        return this;
    }

    @Override
    protected void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(ImageQueryParser.NAME);

        builder.startObject(fieldName);
        builder.field("feature", feature);

        if (image != null) {
            builder.field("image", image);
        }


        if (lookupIndex != null) {
            builder.field("index", lookupIndex);
        }
        builder.field("type", lookupType);
        builder.field("id", lookupId);
        if (lookupRouting != null) {
            builder.field("routing", lookupRouting);
        }
        builder.field("path", lookupPath);

        if (hash != null) {
            builder.field("hash", hash);
        }

        if (boost != -1) {
            builder.field("boost", boost);
        }

        if (limit != -1) {
            builder.field("limit", limit);
        }

        builder.endObject();

        builder.endObject();
    }


}
