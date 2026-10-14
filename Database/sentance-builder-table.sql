-- table to hold imported words and map said words to a word_id
-- one row per unique word found
Create table words(
word_id int Primary Key auto_increment, -- surrogate, the other tables reference by word_id rather than by the word
word VARCHAR(255) not null UNIQUE, -- word from the text, linked to a given word_id
total_frequency int not NULL,	-- how often a word appears in text, must always take in a value
start_frequency int  not null default 0 , -- how often a word is at the start of a sentence, if the col is left out in insert -> 0, cant accept null
end_frequency int not null default 0 -- how often a word is at the end of a sentence, if the col is left out in insert -> 0, cant accept null
);

-- table to hold the transitions
-- one row per one pair (word -> next word)
Create table transitions(
word_id int, -- the first (from) word id
next_word_id int, -- the second (to) word id
frequency int not null default 0,  -- how often we have one specific word transition x -> y
primary key(word_id, next_word_id), -- pk will be the pair of word x -> y
foreign key (word_id) references words(word_id),
foreign key(next_word_id) references words(word_id)
);

-- table to hold the list of imported files, the name of the file, wordcount and import date
-- one tow per text file imported, maps file id ot file name
Create table imported_files(
file_id int Primary Key auto_increment,
filename VARCHAR(255) not null UNIQUE, -- unique to avoid importing the same text file multiple times
wordcount int not null default 0,	-- how many words are in this text file
import_date DATETIME DEFAULT NOW() not null	-- the date + time the text file was imported
);