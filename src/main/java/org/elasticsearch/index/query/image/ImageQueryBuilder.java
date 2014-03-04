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
        builder.field("image", image);

        if (hash != null) {
            builder.field("hash", hash);
        }

        if (boost != -1) {
            builder.field("boost", boost);
        }

        builder.endObject();

        builder.endObject();
    }


}
