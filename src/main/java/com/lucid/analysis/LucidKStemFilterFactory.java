package com.lucid.analysis;

import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.KeywordMarkerFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.solr.analysis.BaseTokenFilterFactory;
import org.apache.solr.common.ResourceLoader;
import org.apache.solr.util.plugin.ResourceLoaderAware;

import java.io.IOException;
import java.util.List;

/**
 * Copyright 2008, Lucid Imagination, Inc.
 */
public class LucidKStemFilterFactory extends BaseTokenFilterFactory implements ResourceLoaderAware {
  public void inform(ResourceLoader loader) {
    assureMatchVersion();
    String wordFile = args.get("protected");
    if (wordFile != null) {
      try {
        List<String> wlist = loader.getLines(wordFile);
        protectedWords = new CharArraySet(luceneMatchVersion, wlist, false);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private CharArraySet protectedWords = null;

  public LucidKStemFilter create(TokenStream input) {
    if (protectedWords != null)
      input = new KeywordMarkerFilter(input, protectedWords);
    return new LucidKStemFilter(input);
  }
}