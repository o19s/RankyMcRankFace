package ciir.umass.edu.parsing;

import ciir.umass.edu.utilities.RankLibError;

/**
 * Created by doug on 5/24/17.
 */
public class ModelLineProducer {

    private StringBuilder model = new StringBuilder();

    public interface LineConsumer {
        void nextLine(StringBuilder model);
    }

    public StringBuilder getModel() {
        return model;
    }

    ;

    public void parse(String fullText, LineConsumer modelConsumer) {


        int CARRIAGE_RETURN = 13;
        int LINE_FEED = 10;

        try {
            String content = "";
            // StringBuilder model = new StringBuilder();

            char[] fullTextChar = fullText.toCharArray();

            int beginOfLineCursor = 0;
            for (int i = 0; i < fullTextChar.length; i++) {
                int charNum = fullTextChar[i];
                if (charNum == CARRIAGE_RETURN || charNum == LINE_FEED) {

                    // NEWLINE, read beginOfLineCursor -> i
                    if (fullTextChar[beginOfLineCursor] != '#') {
                        int eolCursor = i;
                        while (eolCursor > beginOfLineCursor && fullTextChar[eolCursor] <= 32) {
                            eolCursor--;
                        }

                        for (int j = beginOfLineCursor; j <= eolCursor; j++) {
                            model.append(fullTextChar[j]);
                        }
                        modelConsumer.nextLine(model);
                    }

                    // readahead this new line up to the next space
                    while (charNum <= 32 & i < fullTextChar.length) {
                        charNum = fullTextChar[i];
                        beginOfLineCursor = i;
                        i++;
                    }
                }
            }

            // read beginOfLineCursor -> EOF
            if (fullTextChar[beginOfLineCursor] != '#') {
                for (int j = beginOfLineCursor; j < fullTextChar.length; j++) {
                    model.append(fullTextChar[j]);
                }
            }
            modelConsumer.nextLine(model);


        }
        catch(Exception ex)
        {
            throw RankLibError.create("Error in model loading ", ex);
        }
    }
}
