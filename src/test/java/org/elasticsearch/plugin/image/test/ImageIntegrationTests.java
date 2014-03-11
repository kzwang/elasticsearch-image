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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.elasticsearch.client.Requests.putMappingRequest;
import static org.elasticsearch.common.io.Streams.copyToStringFromClasspath;
import static org.elasticsearch.common.settings.ImmutableSettings.settingsBuilder;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertNoFailures;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

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
                .put("index.numberOfReplicas", 0)
                .put("index.number_of_shards", 5)
            .build();
    }

    @Test
    public void test_index_search_image() throws Exception {
        String mapping = copyToStringFromClasspath("/mapping/test-mapping.json");
        client().admin().indices().putMapping(putMappingRequest("test").type("test").source(mapping)).actionGet();

        int totalImages = randomIntBetween(10, 50);

        // generate random images and index
        String nameToSearch = null;
        byte[] imgToSearch = null;
        for (int i = 0; i < totalImages; i ++) {
            int width = randomIntBetween(10, 50);
            int height = randomIntBetween(10, 50);
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            for (int j = 0; j < width; j ++) {
                for (int k = 0; k < height; k ++) {
                    image.setRGB(j, k, randomInt(256));
                }
            }
            byte[] imageByte =  imageToBytes(image);
            String name = randomAsciiOfLength(5);
            index("test", "test", jsonBuilder().startObject().field("img", imageByte).field("name", name).endObject());
            if (nameToSearch == null || imgToSearch == null) {
                nameToSearch = name;
                imgToSearch = imageByte;
            }
        }


        refresh();

        // test search with hash
        ImageQueryBuilder imageQueryBuilder = new ImageQueryBuilder("img").feature(FeatureEnum.CEDD.name()).image(imgToSearch).hash(HashEnum.BIT_SAMPLING.name());
        SearchResponse searchResponse = client().prepareSearch("test").setTypes("test").setQuery(imageQueryBuilder).get();
        assertNoFailures(searchResponse);
        SearchHits hits = searchResponse.getHits();
        assertThat("Should match at least one image", hits.getTotalHits(), greaterThanOrEqualTo(1l)); // if using hash, total result maybe different than number of images
        SearchHit hit = hits.getHits()[0];
        assertThat("First should be exact match and has score 1", hit.getScore(), equalTo(1.0f));
        assertImageScore(hits, nameToSearch, 1.0f);

        // test search without hash and with boost
        ImageQueryBuilder imageQueryBuilder2 = new ImageQueryBuilder("img").feature(FeatureEnum.JCD.name()).image(imgToSearch).boost(2.0f);
        SearchResponse searchResponse2 = client().prepareSearch("test").setTypes("test").setQuery(imageQueryBuilder2).get();
        assertNoFailures(searchResponse2);
        SearchHits hits2 = searchResponse2.getHits();
        assertThat("Should get all images", hits2.getTotalHits(), equalTo((long)totalImages));  // no hash used, total result should be same as number of images
        assertThat("First should be exact match and has score 2", searchResponse2.getHits().getMaxScore(), equalTo(2.0f));
        assertImageScore(hits2, nameToSearch, 2.0f);

        // test search for name as well
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.must(QueryBuilders.termQuery("name", nameToSearch));
        boolQueryBuilder.must(new ImageQueryBuilder("img").feature(FeatureEnum.JCD.name()).image(imgToSearch));
        SearchResponse searchResponse3 = client().prepareSearch("test").setTypes("test").setQuery(boolQueryBuilder).get();
        assertNoFailures(searchResponse3);
        SearchHits hits3 = searchResponse3.getHits();
        assertThat("Should match one document only", hits3.getTotalHits(), equalTo(1l)); // added filename to query, should have only one result
        SearchHit hit3 = hits3.getHits()[0];
        assertThat((String)hit3.getSource().get("name"), equalTo(nameToSearch));

        // test search with hash and limit
        ImageQueryBuilder imageQueryBuilder4 = new ImageQueryBuilder("img").feature(FeatureEnum.JCD.name()).image(imgToSearch).hash(HashEnum.BIT_SAMPLING.name()).limit(10);
        SearchResponse searchResponse4 = client().prepareSearch("test").setTypes("test").setQuery(imageQueryBuilder4).get();
        assertNoFailures(searchResponse4);
        SearchHits hits4 = searchResponse4.getHits();
        assertThat("Should match at least one image", hits4.getTotalHits(), greaterThanOrEqualTo(1l)); // if using hash, total result maybe different than number of images
        SearchHit hit4 = hits4.getHits()[0];
        assertThat("First should be exact match and has score 1", hit4.getScore(), equalTo(1.0f));
        assertImageScore(hits4, nameToSearch, 1.0f);

    }

    private void assertImageScore(SearchHits hits, String name, float score) {
        for (SearchHit hit : hits) {
            if (hit.getSource().get("name").equals(name)){
                assertThat(hit.getScore(), equalTo(score));
                return;
            }
        }
        throw new AssertionError("Image " + name + " not found");
    }


    private byte[] imageToBytes(BufferedImage bufferedImage) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(bufferedImage, "jpg", baos);
        baos.flush();
        byte[] bytes = baos.toByteArray();
        baos.close();
        return bytes;
    }
}
