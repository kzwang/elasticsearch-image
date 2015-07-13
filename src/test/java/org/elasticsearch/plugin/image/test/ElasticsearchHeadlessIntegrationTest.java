package org.elasticsearch.plugin.image.test;

import org.elasticsearch.test.ElasticsearchIntegrationTest;
import org.junit.After;

import java.io.Closeable;

/**
 * Created by @marcos-carceles on 13/07/15.
 */
//TODO: Temporary, remove when a proper fix is found
public class ElasticsearchHeadlessIntegrationTest extends ElasticsearchIntegrationTest {

    @After
    public void cleanupSystemProperties() throws Exception {
        System.getProperties().remove("sun.font.fontmanager");
    }
}
