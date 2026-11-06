/*
* Document Metadata Class
* As it sounds, stores metadata information collected from Project Gutenberg
* documents. Will be created by a GutenbergIngestor object upon first opening
* a file. All fields are fairly self-explanatory, bookNumber refers to Project
* Gutenberg's internal numbering system for their documents. As all methods are
* getters, setters, or constructors, there is little internal documentation in this
* module.
*
* Written by Gabriel Goble (GGG220000) for CS4485.0W1, Sentence-Builder Project,
* starting October 16, 2026
*/
package backend.Ingestion;
public class DocumentMetadata {
    private String Title;
    private String Author;
    private String Date;
    private int bookNumber;
    private long sizeBytes;

    public DocumentMetadata(){}
    public DocumentMetadata(String title, String author, String date, int bookNumber, long sizeBytes){
        this.Title = title;
        this.Author = author;
        this.Date = date;
        this.bookNumber = bookNumber;
        this.sizeBytes = sizeBytes;
    }

    public String getTitle(){
        return Title;
    }
    public void setTitle(String title){
        this.Title = title;
    }

    public String getAuthor(){
        return Author;
    }
    public void setAuthor(String author){
        this.Author = author;
    }

    public String getDate(){
        return Date;
    }
    public void setDate(String date){
        this.Date = date;
    }

    public int getBookNumber(){
        return bookNumber;
    }
    public void setBookNumber(int bookNumber){
        this.bookNumber = bookNumber;
    }

    public long getSizeBytes(){
        return sizeBytes;
    }
    public void setSizeBytes(int sizeBytes){
        this.sizeBytes = sizeBytes;
    }
}
