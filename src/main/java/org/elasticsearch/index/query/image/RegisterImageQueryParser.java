package org.elasticsearch.index.query.image;

import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.AbstractIndexComponent;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.settings.IndexSettings;
import org.elasticsearch.indices.query.IndicesQueriesRegistry;


public class RegisterImageQueryParser extends AbstractIndexComponent {

    @Inject
    protected RegisterImageQueryParser(Index index, @IndexSettings Settings indexSettings,
                                       IndicesQueriesRegistry indicesQueriesRegistry,
                                       ImageQueryParser parser) {
        super(index, indexSettings);
        indicesQueriesRegistry.addQueryParser(parser);
    }
}
