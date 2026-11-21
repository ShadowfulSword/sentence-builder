-- updated to for easier copy pasting to run among other computers
-- also creates the DB and the tables now
-- Had class rewritten to reflect a change in the schema, had Gemini handle the rewriting
-- Prompt: can you modify the sql to have the words table have an int as a primary index and the
-- transitions to use that primary key as the foreign key. Can you also then go and modify the
-- databasemanager class to ensure all the interfaces work/output the same things they did before
-- but with the new schema, no mistakes.

CREATE DATABASE IF NOT EXISTS sentence_builder;

USE sentence_builder;

-- table to hold imported words
-- one row per unique word found
CREATE TABLE IF NOT EXISTS words(
                       id INT AUTO_INCREMENT PRIMARY KEY,
                       word VARCHAR(255) NOT NULL UNIQUE, -- word from the text, stored directly into the DB
                       total_frequency INT NOT NULL, -- how often a word appears in text, must always take in a value
                       start_frequency INT NOT NULL DEFAULT 0, -- how often a word is at the start of a sentence, if the col is left out in insert -> 0, cant accept null
                       end_frequency INT NOT NULL DEFAULT 0 -- how often a word is at the end of a sentence, if the col is left out in insert -> 0, cant accept null
);

-- table to hold the transitions
-- one row per one pair (word -> next word)
CREATE TABLE IF NOT EXISTS transitions (
                             word_id INT NOT NULL, -- the first (from) word
                             next_word_id INT NOT NULL, -- the second (to) word
                             frequency INT NOT NULL DEFAULT 0, -- how often we have one specific word transition x -> y
                             PRIMARY KEY (word_id, next_word_id), -- pk will be the pair of word_id x -> next_word_id y
                             FOREIGN KEY (word_id) REFERENCES words(id) ON UPDATE CASCADE ON DELETE CASCADE,
                             FOREIGN KEY (next_word_id) REFERENCES words(id) ON UPDATE CASCADE ON DELETE CASCADE
);

-- table to hold the list of imported files, the name of the file, word count and import date
-- one row per text file imported, maps file id to file name
CREATE TABLE IF NOT EXISTS imported_files (
                                file_id INT PRIMARY KEY AUTO_INCREMENT,
                                filename VARCHAR(255) NOT NULL UNIQUE, -- unique to avoid importing the same text file multiple times
                                wordcount INT NOT NULL DEFAULT 0, -- how many words are in this text file
                                import_date DATETIME DEFAULT NOW() NOT NULL -- the date + time the text file was imported
);

-- table to hold the full sentances that are self ingested and to be shown in the reports page
CREATE TABLE IF NOT Exists generated_sentences(
    sentence_id INT PRIMARY KEY AUTO_INCREMENT,
    sentence TEXT not NULL, -- generated sentence
    algorithm VARCHAR(100) NOT NULL, -- algo used
    self_ingested BOOLEAN NOT NULL DEFAULT FALSE, -- check for if it was self ingested so we can internally differencitate just in case
    generated_at DATETIME DEFAULT NOW() NOT NULL -- date + time of text generated
);