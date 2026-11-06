-- updated to for easier copy pasting to run among other computers
-- also creates the DB and the tables now
CREATE DATABASE IF NOT EXISTS sentence_builder;

USE sentence_builder;
-- table to hold imported words
-- one row per unique word found
Create table words(
                      word VARCHAR(255) PRIMARY KEY , -- word from the text, stored directly into the DB
                      total_frequency int not NULL,	-- how often a word appears in text, must always take in a value
                      start_frequency int  not null default 0 , -- how often a word is at the start of a sentence, if the col is left out in insert -> 0, cant accept null
                      end_frequency int not null default 0 -- how often a word is at the end of a sentence, if the col is left out in insert -> 0, cant accept null
);

-- table to hold the transitions
-- one row per one pair (word -> next word)
Create table transitions(
                            word VARCHAR(255), -- the first (from) word
                            next_word VARCHAR(255), -- the second (to) word
                            frequency int not null default 0,  -- how often we have one specific word transition x -> y
                            primary key(word, next_word), -- pk will be the pair of word x -> y
                            foreign key (word) references words(word) ON UPDATE CASCADE ,
                            foreign key(next_word) references words(word) ON UPDATE CASCADE
);

-- table to hold the list of imported files, the name of the file, word count and import date
-- one tow per text file imported, maps file id ot file name
Create table imported_files(
                               file_id int Primary Key auto_increment,
                               filename VARCHAR(255) not null UNIQUE, -- unique to avoid importing the same text file multiple times
                               wordcount int not null default 0,	-- how many words are in this text file
                               import_date DATETIME DEFAULT NOW() not null	-- the date + time the text file was imported
);