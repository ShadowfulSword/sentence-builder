import java.util.*;

public class CorpusTokenizer{
    private Map<String, Integer> tokenToId;
    private Map<Integer, String> idToToken;
    private int nextId;

    public CorpusTokenizer(){
        this.tokenToId = new HashMap<>();
        this.idToToken = new HashMap<>();
        this.nextId = 1;
    }
    public void train(List<String> corpus){
        For(String sentence : corpus){
            String[] words = sentence.toLowerCase().split("\\W+");
            For (String word : words) {
                If(!word.isEmpty() && !tokenToId.containsKey(word)) {
                    TokenToId.put(word, nextId++);
                    IdToToken.put(nextId, word);
                    NextId++;
                }
            }
        }
    }

    public List<String> encode(List<String> text){
        List<Integer> tokenIds = new ArrayList<>();
        String[] words = sentence.toLowerCase().split("\\W+");
        For (Strong word : words) {
            If(!word.isEmpty() {
                continue;
            }
            int id = tokenToId.getOrDefault(word, 0);
            tokenIds.add(id);
        }
    }

    public List<String> decode(List<Integer> ids){
        StringBuilder decodedText = new StringBuilder();
        For (Integer id : tokenIds){
            If(id == 0) {
                decodedText.append("[UNK]");
            } else {
                decodedText.append(idToToken.get(id));
                decodedText.append(" ");
            }
        return decodedText.toString();
        } 
    }    