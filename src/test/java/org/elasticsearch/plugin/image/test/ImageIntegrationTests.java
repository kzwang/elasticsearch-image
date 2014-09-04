package org.elasticsearch.plugin.image.test;

import org.apache.sanselan.ImageFormat;
import org.apache.sanselan.ImageWriteException;
import org.apache.sanselan.Sanselan;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.collect.Maps;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.mapper.image.FeatureEnum;
import org.elasticsearch.index.mapper.image.HashEnum;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.index.query.image.ImageQueryBuilder;
import org.elasticsearch.plugins.PluginsService;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.test.ElasticsearchIntegrationTest;
import org.junit.Before;
import org.junit.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;

import static org.elasticsearch.client.Requests.putMappingRequest;
import static org.elasticsearch.common.io.Streams.copyToStringFromClasspath;
import static org.elasticsearch.common.settings.ImmutableSettings.settingsBuilder;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertNoFailures;
import static org.hamcrest.Matchers.*;

@ElasticsearchIntegrationTest.ClusterScope(scope = ElasticsearchIntegrationTest.Scope.SUITE)
public class ImageIntegrationTests extends ElasticsearchIntegrationTest {

    private final static String INDEX_NAME = "test";
    private final static String DOC_TYPE_NAME = "test";


    @Override
    protected Settings nodeSettings(int nodeOrdinal) {
        return ImmutableSettings.builder()
                .put(super.nodeSettings(nodeOrdinal))
                .put("plugins." + PluginsService.LOAD_PLUGIN_FROM_CLASSPATH, true)
                .build();
    }

    @Before
    public void createEmptyIndex() throws Exception {
        logger.info("creating index [{}]", INDEX_NAME);
        createIndex(INDEX_NAME);
        ensureGreen();
    }

    @Override
    public Settings indexSettings() {
        return settingsBuilder()
                .put("index.number_of_replicas", 0)
                .put("index.number_of_shards", 5)
                .put("index.image.use_thread_pool", randomBoolean())
            .build();
    }

    @Test
    public void test_index_search_image() throws Exception {
        String mapping = copyToStringFromClasspath("/mapping/test-mapping.json");
        client().admin().indices().putMapping(putMappingRequest(INDEX_NAME).type(DOC_TYPE_NAME).source(mapping)).actionGet();

        int totalImages = randomIntBetween(10, 50);

        // generate random images and index
        String nameToSearch = null;
        byte[] imgToSearch = null;
        String idToSearch = null;
        for (int i = 0; i < totalImages; i ++) {
            byte[] imageByte = getRandomImage();
            String name = randomAsciiOfLength(5);
            IndexResponse response = index(INDEX_NAME, DOC_TYPE_NAME, jsonBuilder().startObject().field("img", imageByte).field("name", name).endObject());
            if (nameToSearch == null || imgToSearch == null || idToSearch == null) {
                nameToSearch = name;
                imgToSearch = imageByte;
                idToSearch = response.getId();
            }
        }

        refresh();

        // test search with hash
        ImageQueryBuilder imageQueryBuilder = new ImageQueryBuilder("img").feature(FeatureEnum.CEDD.name()).image(imgToSearch).hash(HashEnum.BIT_SAMPLING.name());
        SearchResponse searchResponse = client().prepareSearch(INDEX_NAME).setTypes(DOC_TYPE_NAME).setQuery(imageQueryBuilder).addFields("img.metadata.exif_ifd0.x_resolution", "name").setSize(totalImages).get();
        assertNoFailures(searchResponse);
        SearchHits hits = searchResponse.getHits();
        assertThat("Should match at least one image", hits.getTotalHits(), greaterThanOrEqualTo(1l)); // if using hash, total result maybe different than number of images
        SearchHit hit = hits.getHits()[0];
        assertThat("First should be exact match and has score 1", hit.getScore(), equalTo(2.0f));
        assertImageScore(hits, nameToSearch, 2.0f);
        assertThat("Should have metadata", hit.getFields().get("img.metadata.exif_ifd0.x_resolution").getValues(), hasSize(1));

        // test search without hash and with boost
        ImageQueryBuilder imageQueryBuilder2 = new ImageQueryBuilder("img").feature(FeatureEnum.CEDD.name()).image(imgToSearch).boost(2.0f);
        SearchResponse searchResponse2 = client().prepareSearch(INDEX_NAME).setTypes(DOC_TYPE_NAME).setQuery(imageQueryBuilder2).setSize(totalImages).get();
        assertNoFailures(searchResponse2);
        SearchHits hits2 = searchResponse2.getHits();
        assertThat("Should get all images", hits2.getTotalHits(), equalTo((long)totalImages));  // no hash used, total result should be same as number of images
        assertThat("First should be exact match and has score 2", searchResponse2.getHits().getMaxScore(), equalTo(4.0f));
        assertImageScore(hits2, nameToSearch, 4.0f);

        // test search for name as well
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.must(QueryBuilders.termQuery("name", nameToSearch));
        boolQueryBuilder.must(new ImageQueryBuilder("img").feature(FeatureEnum.CEDD.name()).image(imgToSearch));
        SearchResponse searchResponse3 = client().prepareSearch(INDEX_NAME).setTypes(DOC_TYPE_NAME).setQuery(boolQueryBuilder).setSize(totalImages).get();
        assertNoFailures(searchResponse3);
        SearchHits hits3 = searchResponse3.getHits();
        assertThat("Should match one document only", hits3.getTotalHits(), equalTo(1l)); // added filename to query, should have only one result
        SearchHit hit3 = hits3.getHits()[0];
        assertThat((String)hit3.getSource().get("name"), equalTo(nameToSearch));

        // test search with hash and limit
        ImageQueryBuilder imageQueryBuilder4 = new ImageQueryBuilder("img").feature(FeatureEnum.CEDD.name()).image(imgToSearch).hash(HashEnum.BIT_SAMPLING.name()).limit(10);
        SearchResponse searchResponse4 = client().prepareSearch(INDEX_NAME).setTypes(DOC_TYPE_NAME).setQuery(imageQueryBuilder4).setSize(totalImages).get();
        assertNoFailures(searchResponse4);
        SearchHits hits4 = searchResponse4.getHits();
        assertThat("Should match at least one image", hits4.getTotalHits(), greaterThanOrEqualTo(1l)); // if using hash, total result maybe different than number of images
        SearchHit hit4 = hits4.getHits()[0];
        assertThat("First should be exact match and has score 1", hit4.getScore(), equalTo(2.0f));
        assertImageScore(hits4, nameToSearch, 2.0f);

        // test search metadata
        TermQueryBuilder termQueryBuilder = QueryBuilders.termQuery("img.metadata.exif_ifd0.x_resolution", "72 dots per inch");
        SearchResponse searchResponse5 = client().prepareSearch(INDEX_NAME).setTypes(DOC_TYPE_NAME).setQuery(termQueryBuilder).setSize(totalImages).get();
        assertNoFailures(searchResponse5);
        SearchHits hits5 = searchResponse5.getHits();
        assertThat("Should match at least one record", hits5.getTotalHits(), greaterThanOrEqualTo(1l)); // if using hash, total result maybe different than number of images

        // test search with exist image
        ImageQueryBuilder imageQueryBuilder6 = new ImageQueryBuilder("img").feature(FeatureEnum.CEDD.name()).lookupIndex(INDEX_NAME).lookupType(DOC_TYPE_NAME).lookupId(idToSearch).lookupPath("img");
        SearchResponse searchResponse6 = client().prepareSearch(INDEX_NAME).setTypes(DOC_TYPE_NAME).setQuery(imageQueryBuilder6).setSize(totalImages).get();
        assertNoFailures(searchResponse6);
        SearchHits hits6 = searchResponse6.getHits();
        assertThat("Should match at least one image", hits6.getTotalHits(), equalTo((long) totalImages));
        SearchHit hit6 = hits6.getHits()[0];
        assertThat("First should be exact match and has score 1", hit6.getScore(), equalTo(2.0f));
        assertImageScore(hits6, nameToSearch, 2.0f);

        // test search with exist image using hash
        ImageQueryBuilder imageQueryBuilder7 = new ImageQueryBuilder("img").feature(FeatureEnum.CEDD.name()).lookupIndex(INDEX_NAME).lookupType(DOC_TYPE_NAME).lookupId(idToSearch).lookupPath("img").hash(HashEnum.BIT_SAMPLING.name());
        SearchResponse searchResponse7 = client().prepareSearch(INDEX_NAME).setTypes(DOC_TYPE_NAME).setQuery(imageQueryBuilder7).setSize(totalImages).get();
        assertNoFailures(searchResponse7);
        SearchHits hits7 = searchResponse7.getHits();
        assertThat("Should match at least one image", hits7.getTotalHits(), equalTo((long) totalImages));
        SearchHit hit7 = hits7.getHits()[0];
        assertThat("First should be exact match and has score 1", hit7.getScore(), equalTo(2.0f));
        assertImageScore(hits7, nameToSearch, 2.0f);
    }

    private void assertImageScore(SearchHits hits, String name, float score) {
        for (SearchHit hit : hits) {
            if ((hit.getSource() != null && hit.getSource().get("name").equals(name))
                    || (hit.getFields() != null && !hit.getFields().isEmpty() && hit.getFields().get("name").getValue().equals(name))){
                assertThat(hit.getScore(), equalTo(score));
                return;
            }
        }
        throw new AssertionError("Image " + name + " not found");
    }

    private byte[] getRandomImage() throws IOException, ImageWriteException {
        int width = randomIntBetween(100, 1000);
        int height = randomIntBetween(100, 1000);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int j = 0; j < width; j ++) {
            for (int k = 0; k < height; k ++) {
                image.setRGB(j, k, randomInt(512));
            }
        }
        ImageFormat format = ImageFormat.IMAGE_FORMAT_TIFF;
        byte[] bytes = Sanselan.writeImageToBytes(image, format, Maps.newHashMap());
        return bytes;
    }
}
