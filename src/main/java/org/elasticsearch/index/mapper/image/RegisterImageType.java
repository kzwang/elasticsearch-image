package org.elasticsearch.index.mapper.image;

import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.AbstractIndexComponent;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.settings.IndexSettings;
import org.elasticsearch.threadpool.ThreadPool;

public class RegisterImageType extends AbstractIndexComponent {

    @Inject
    public RegisterImageType(Index index, @IndexSettings Settings indexSettings, MapperService mapperService, ThreadPool threadPool) {
        super(index, indexSettings);
        mapperService.documentMapperParser().putTypeParser("image", new ImageMapper.TypeParser(threadPool));
    }
}
