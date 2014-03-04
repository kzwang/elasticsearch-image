package org.elasticsearch.plugin.image;

import org.elasticsearch.common.inject.AbstractModule;
import org.elasticsearch.index.mapper.image.RegisterImageType;
import org.elasticsearch.index.query.image.RegisterImageQueryParser;


public class ImageIndexModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(RegisterImageType.class).asEagerSingleton();
        bind(RegisterImageQueryParser.class).asEagerSingleton();
    }
}
