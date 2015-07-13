package org.elasticsearch.index.query.image;

import java.io.IOException;
import java.util.Set;

import net.semanticmetadata.lire.imageanalysis.LireFeature;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.ToStringUtils;

/**
 * Copied from {@link TermQuery}, query by hash first and only calculate score for matching docs
 */
public class ImageHashQuery extends Query {
    private final Term term;

    private String luceneFieldName;
    private LireFeature lireFeature;
    private ImageScoreCache imageScoreCache;

    final class ImageHashScorer extends AbstractImageScorer {
        private final DocsEnum docsEnum;
        private final IndexReader reader;

        ImageHashScorer(Weight weight, DocsEnum td, IndexReader reader) {
            super(weight, luceneFieldName, lireFeature, reader, ImageHashQuery.this.getBoost());
            this.docsEnum = td;
            this.reader = reader;
        }

        @Override
        public int docID() {
            return docsEnum.docID();
        }


        @Override
        public int nextDoc() throws IOException {
            return docsEnum.nextDoc();
        }

        @Override
        public float score() throws IOException {
            assert docID() != NO_MORE_DOCS;
            int docId = docID();
            String cacheKey = reader.toString() + ":" + docId;
            if (imageScoreCache.getScore(cacheKey) != null) {
                return 0f;  // BooleanScorer will add all score together, return 0 for docs already processed
            }
            float score = super.score();
            imageScoreCache.setScore(cacheKey, score);
            return score;
        }

        @Override
        public int advance(int target) throws IOException {
            return docsEnum.advance(target);
        }

        @Override
        public long cost() {
            return docsEnum.cost();
        }
    }

    final class ImageHashWeight extends Weight {
        private final TermContext termStates;

        public ImageHashWeight(IndexSearcher searcher, TermContext termStates)
                throws IOException {
            assert termStates != null : "TermContext must not be null";
            this.termStates = termStates;
        }

        @Override
        public String toString() { return "weight(" + ImageHashQuery.this + ")"; }

        @Override
        public Query getQuery() { return ImageHashQuery.this; }

        @Override
        public float getValueForNormalization() {
            return 1f;
        }

        @Override
        public void normalize(float queryNorm, float topLevelBoost) {
        }

        @Override
        public Scorer scorer(AtomicReaderContext context, Bits acceptDocs) throws IOException {
            assert termStates.topReaderContext == ReaderUtil.getTopLevelContext(context) : "The top-reader used to create Weight (" + termStates.topReaderContext + ") is not the same as the current reader's top-reader (" + ReaderUtil.getTopLevelContext(context);
            final TermsEnum termsEnum = getTermsEnum(context);
            if (termsEnum == null) {
                return null;
            }
            DocsEnum docs = termsEnum.docs(acceptDocs, null);
            assert docs != null;
            return new ImageHashScorer(this, docs, context.reader());
        }

        private TermsEnum getTermsEnum(AtomicReaderContext context) throws IOException {
            final TermState state = termStates.get(context.ord);
            if (state == null) { // term is not present in that reader
                assert termNotInReader(context.reader(), term) : "no termstate found but term exists in reader term=" + term;
                return null;
            }
            final TermsEnum termsEnum = context.reader().terms(term.field()).iterator(null);
            termsEnum.seekExact(term.bytes(), state);
            return termsEnum;
        }

        private boolean termNotInReader(AtomicReader reader, Term term) throws IOException {
            return reader.docFreq(term) == 0;
        }

        @Override
        public Explanation explain(AtomicReaderContext context, int doc) throws IOException {
            Scorer scorer = scorer(context, context.reader().getLiveDocs());
            if (scorer != null) {
                int newDoc = scorer.advance(doc);
                if (newDoc == doc) {
                    float score = scorer.score();
                    ComplexExplanation result = new ComplexExplanation();
                    result.setDescription("ImageHashQuery, product of:");
                    result.setValue(score);
                    if (getBoost() != 1.0f) {
                        result.addDetail(new Explanation(getBoost(),"boost"));
                        score = score / getBoost();
                    }
                    result.addDetail(new Explanation(score ,"image score (1/distance)"));
                    result.setMatch(true);
                    return result;
                }
            }

            return new ComplexExplanation(false, 0.0f, "no matching term");
        }
    }

    public ImageHashQuery(Term t, String luceneFieldName, LireFeature lireFeature, ImageScoreCache imageScoreCache, float boost) {
        this.term = t;
        this.luceneFieldName = luceneFieldName;
        this.lireFeature = lireFeature;
        this.imageScoreCache = imageScoreCache;
        setBoost(boost);
    }

    public Term getTerm() {
        return term;
    }

    @Override
    public Weight createWeight(IndexSearcher searcher) throws IOException {
        final IndexReaderContext context = searcher.getTopReaderContext();
        final TermContext termState = TermContext.build(context, term);
        return new ImageHashWeight(searcher, termState);
    }

    @Override
    public void extractTerms(Set<Term> terms) {
        terms.add(getTerm());
    }

    @Override
    public String toString(String field) {
        StringBuilder buffer = new StringBuilder();
        if (!term.field().equals(field)) {
            buffer.append(term.field());
            buffer.append(":");
        }
        buffer.append(term.text());
        buffer.append(";");
        buffer.append(luceneFieldName);
        buffer.append(",");
        buffer.append(lireFeature.getClass().getSimpleName());
        buffer.append(ToStringUtils.boost(getBoost()));
        return buffer.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ImageHashQuery))
            return false;
        ImageHashQuery other = (ImageHashQuery)o;
        return (this.getBoost() == other.getBoost())
                && this.term.equals(other.term)
                & luceneFieldName.equals(luceneFieldName)
                && lireFeature.equals(lireFeature);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + term.hashCode();
        result = 31 * result + luceneFieldName.hashCode();
        result = 31 * result + lireFeature.hashCode();
        result = Float.floatToIntBits(getBoost()) ^ result;
        return result;
    }
}
