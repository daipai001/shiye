ALTER TABLE `rag_eval_runs`
  ADD COLUMN `last_progress_at` DATETIME(3) NULL DEFAULT NULL AFTER `started_at`;

UPDATE `rag_eval_runs`
SET `last_progress_at` = `started_at`
WHERE `last_progress_at` IS NULL AND `started_at` IS NOT NULL;
