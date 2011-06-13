// Copyright 2008, Lucid Imagination, Inc.

package com.lucid.analysis;

import org.apache.lucene.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.WhitespaceTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.util.Version;
import org.apache.solr.analysis.PorterStemFilterFactory;
import org.apache.solr.core.SolrResourceLoader;
import org.junit.Test;

import java.io.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class TestKStemmer extends BaseTokenStreamTestCase {

  private static String LUCIDWORKS_BASEDIR = "./";

  public void testNoProtected() throws IOException {
    SolrResourceLoader loader = new SolrResourceLoader("solr/cores/collection1", null);
    final LucidKStemFilterFactory factory = new LucidKStemFilterFactory();
    factory.init(Collections.singletonMap("luceneMatchVersion", Version.LUCENE_CURRENT.toString()));
    factory.inform(loader);
    TokenStream stream = factory.create(new WhitespaceTokenizer(Version.LUCENE_CURRENT, new StringReader("cats dontstems")));
    assertTokenStreamContents(stream, new String[]{"cat", "dontstem"});
  }

  public void testProtected() throws IOException {
    SolrResourceLoader loader = new SolrResourceLoader("solr/cores/collection1", null);
    final LucidKStemFilterFactory factory = new LucidKStemFilterFactory();
    Map<String, String> args = new HashMap<String, String>();
    args.put("luceneMatchVersion", Version.LUCENE_CURRENT.toString());
    args.put("protected", "protwords.txt");
    factory.init(args);
    factory.inform(loader);
    TokenStream stream = factory.create(new WhitespaceTokenizer(Version.LUCENE_CURRENT, new StringReader("cats dontstems")));
    assertTokenStreamContents(stream, new String[]{"cat", "dontstems"});
  }

  public String getBigDoc() throws Exception {
    InputStream is = new FileInputStream(new File(LUCIDWORKS_BASEDIR, "src/test/resources/history_of_the_united_states.txt"));
    byte[] b = new byte[1400000];
    int len = is.read(b);
    is.close();
    System.out.println("read " + len + " bytes");
    return new String(b, 0, len, "UTF-8");
  }

// TODO: fix this
//  Map<Integer,Token[]> map = new HashMap<Integer,Token[]>();t
//  TokenStream getCachedStream(int num, String s) throws IOException {
//    Token[] result = map.get(num);
//    if (result == null) {
//      TokenStream ts = new WhitespaceTokenizer(new StringReader(s));
//      ts = new LowerCaseFilter(ts);
//      List<Token> lst = new ArrayList<Token>();
//      for(;;) {
//        Token t = ts.next();
//        if (t==null) break;
//        lst.add(t);
//      }
//      result = lst.toArray(new Token[lst.size()]);
//      map.put(num, result);
//    }
//
//    final Token[] tokens = result;
//    TokenStream ts = new TokenStream() {
//      int pos=0;
//      @Override
//      public Token next(Token result) throws IOException {
//        if (pos >= tokens.length) return null;
//        Token t = tokens[pos++];
//        result.setTermBuffer(t.termBuffer(), 0, t.termLength());
//        return result;
//      }
//    };
//
//    return ts;
//  }

  private PorterStemFilterFactory engPorterFilter = new PorterStemFilterFactory();

  boolean cache = true;

  TokenStream createStream(int n, String s) throws IOException {
    TokenStream ts;
    // TODO: re-enable cache
    //if (cache) {
    //  ts = getCachedStream(0,s);
    // } else {
    Reader r = new StringReader(s);
    ts = new WhitespaceTokenizer(Version.LUCENE_CURRENT, r);
    ts = new LowerCaseFilter(Version.LUCENE_CURRENT, ts);
    //}
    ts = new LucidKStemFilter(ts);
    // ts = new KStemFilter(ts);
    // ts = engPorterFilter.create(ts);
    return ts;
  }


  public void doPerfTest(String[] vals, int iter) throws IOException {
    int ret = 0;

    long num = 0;
    long start = System.currentTimeMillis();

    for (int i = 0; i < iter; i++) {
      if (i == 1) {
        start = System.currentTimeMillis(); // first iter is warmup
        num = 0;
        ret = 0;
      }
      for (int valnum = 0; valnum < vals.length; valnum++) {
        String val = vals[valnum];
        if (val.length() == 0) continue;
        TokenStream ts = createStream(valnum, val);
        CharTermAttribute termAttrib = (CharTermAttribute) ts.getAttribute(CharTermAttribute.class);
        for (; ; ) {
          // call the filter chain the same way the lucene indexer does
          if (!ts.incrementToken())
            break;
          // access the buffer + length
          ret += termAttrib.charAt(0);
          ret += termAttrib.length();
          num++;
        }
      }
    }

    long end = System.currentTimeMillis();
    System.out.println("ret=" + ret + " tokens/sec=" + num * 1000 / (end - start));
  }


  public void XtestBigFieldPeformance() throws Exception {
    String big = getBigDoc();
    doPerfTest(new String[]{big}, 10 * 10000);
  }

  public void XtestSmallFieldPeformance() throws Exception {
    String vals[] = getBigDoc().split("\n");
    doPerfTest(vals, 20);
  }


  // test the new kstemmer against a bunch of words
  // that were stemmed with the original java kstemmer (generated from
  // testCreateMap, commented out below).
  @Test
  public void testLucidKStemmer() throws Exception {
    BufferedReader r = new BufferedReader(new FileReader(new File(LUCIDWORKS_BASEDIR, "src/test/resources/kstem_examples.txt")));

    LucidKStemmer kstem = new LucidKStemmer();
    for (; ; ) {
      String line = r.readLine();
      if (line == null) break;
      String[] vals = line.split(" ");

      String answer = kstem.stem(vals[0]);
      if (answer == null) answer = vals[0];
      assertEquals(vals[1], answer);

      boolean changed = kstem.stem(vals[0].toCharArray(), vals[0].length());
      String answer2 = changed ? kstem.getString() : vals[0];
      if (answer2 == null) answer2 = new String(kstem.getChars(), 0, kstem.getLength());
      assertEquals(vals[1], answer2);
    }

    r.close();
  }

  /****** requires original java kstem source code to create map
   public void testCreateMap() throws Exception {
   String input = getBigDoc();
   Reader r = new StringReader(input);
   TokenFilter tf = new LowerCaseFilter(new LetterTokenizer(r));
   // tf = new KStemFilter(tf);

   KStemmer kstem = new KStemmer();
   Map<String,String> map = new TreeMap<String,String>();
   for(;;) {
   Token t = tf.next();
   if (t==null) break;
   String s = t.termText();
   if (map.containsKey(s)) continue;
   map.put(s, kstem.stem(s));
   }

   Writer out = new BufferedWriter(new FileWriter("kstem_examples.txt"));
   for (String key : map.keySet()) {
   out.write(key);
   out.write(' ');
   out.write(map.get(key));
   out.write('\n');
   }
   out.close();
   }
   ******/

}
