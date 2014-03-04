package org.elasticsearch.plugin.image.test;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.mapper.image.FeatureEnum;
import org.elasticsearch.index.mapper.image.HashEnum;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.image.ImageQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.test.ElasticsearchIntegrationTest;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URL;

import static org.elasticsearch.client.Requests.putMappingRequest;
import static org.elasticsearch.common.io.Streams.copyToStringFromClasspath;
import static org.elasticsearch.common.settings.ImmutableSettings.settingsBuilder;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

import static org.hamcrest.Matchers.*;

@ElasticsearchIntegrationTest.ClusterScope(transportClientRatio = 0.0, numNodes = 2, scope = ElasticsearchIntegrationTest.Scope.TEST)
public class ImageIntegrationTests extends ElasticsearchIntegrationTest {

    @Before
    public void createEmptyIndex() throws Exception {
        logger.info("creating index [test]");
        wipeIndices("test");
        createIndex("test");
        ensureGreen();
    }

    @Override
    public Settings indexSettings() {
        return settingsBuilder()
                .put("index.numberOfReplicas", 1)
            .build();
    }

    @Test
    /**
     * Test index and search image
     *
     * It will index all files in "src/test/resources/image" folder
     * All files in that folder must be image file
     * if folder not exist or no file in that folder, test will not run
     */
    public void test_index_search_image() throws Exception {
        URL url = ImageIntegrationTests.class.getResource("/image");
        if (url == null) {
            logger.info("No image files found, ignore test");
            return;  // no test image files, no need to run test
        }
        File[] files = new File(url.toURI()).listFiles();
        if (files.length == 0) {
            logger.info("No image files found, ignore test");
            return;  // no test image files, no need to run test
        }

        logger.info("Test with " + files.length + " images");

        String mapping = copyToStringFromClasspath("/mapping/test-mapping.json");
        client().admin().indices().putMapping(putMappingRequest("test").type("test").source(mapping)).actionGet();


        File fileToSearch = files[randomInt(files.length - 1)];
        byte[] imgToSearch = readFile(fileToSearch);

        for (File file : files) {   // all files in that folder should be image file
            byte[] jpg = readFile(file);
            index("test", "test", jsonBuilder().startObject().field("img", jpg).field("name", file.getName()).endObject());
        }

        refresh();

        // test search with hash
        ImageQueryBuilder imageQueryBuilder = new ImageQueryBuilder("img").feature(FeatureEnum.CEDD.name()).image(imgToSearch).hash(HashEnum.BIT_SAMPLING.name());
        SearchResponse searchResponse = client().prepareSearch("test").setTypes("test").setQuery(imageQueryBuilder).get();
        SearchHits hits = searchResponse.getHits();
        assertThat("Should match at least one image", hits.getTotalHits(), greaterThanOrEqualTo(1l)); // if using hash, total result maybe different than number of images
        SearchHit hit = hits.getHits()[0];
        assertThat("First should be exact match and has score 1", hit.getScore(), equalTo(1.0f));
        assertThat((String)hit.getSource().get("name"), equalTo(fileToSearch.getName()));

        // test search without hash and with boost
        ImageQueryBuilder imageQueryBuilder2 = new ImageQueryBuilder("img").feature(FeatureEnum.JCD.name()).image(imgToSearch).boost(2.0f);
        SearchResponse searchResponse2 = client().prepareSearch("test").setTypes("test").setQuery(imageQueryBuilder2).get();
        SearchHits hits2 = searchResponse2.getHits();
        assertThat("Should match at least one image", hits2.getTotalHits(), equalTo((long)files.length));  // no hash used, total result should be same as number of images
        SearchHit hit2 = hits2.getHits()[0];
        assertThat("First should be exact match and has score 2", hit2.getScore(), equalTo(2.0f));
        assertThat((String)hit2.getSource().get("name"), equalTo(fileToSearch.getName()));

        // test search for name as well
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.must(QueryBuilders.termQuery("name", fileToSearch.getName()));
        boolQueryBuilder.must(new ImageQueryBuilder("img").feature(FeatureEnum.JCD.name()).image(imgToSearch));
        SearchResponse searchResponse3 = client().prepareSearch("test").setTypes("test").setQuery(boolQueryBuilder).get();
        SearchHits hits3 = searchResponse3.getHits();
        assertThat("Should match one document only", hits3.getTotalHits(), equalTo(1l)); // added filename to query, should have only one result
        SearchHit hit3 = hits3.getHits()[0];
        assertThat((String)hit3.getSource().get("name"), equalTo(fileToSearch.getName()));

    }


    public static byte[] readFile(File file) throws IOException {
        RandomAccessFile f = new RandomAccessFile(file, "r");
        try {
            int length = (int) f.length();
            byte[] data = new byte[length];
            f.readFully(data);
            return data;
        } finally {
            f.close();
        }
    }
}
