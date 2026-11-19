-- RAG hybrid retrieval: keyword recall over article chunks.
-- MySQL 8 ngram parser works for Chinese and mixed CJK/Latin text.
ALTER TABLE `article_chunks`
  ADD FULLTEXT KEY `ft_chunks_content` (`content`) WITH PARSER ngram;
