-- RAG 评测保存模型回答和判定说明，便于管理端复核。
ALTER TABLE `rag_eval_results`
  ADD COLUMN `answer_text` TEXT NULL DEFAULT NULL AFTER `answer_correctness`,
  ADD COLUMN `judge_reason` TEXT NULL DEFAULT NULL AFTER `answer_text`;
