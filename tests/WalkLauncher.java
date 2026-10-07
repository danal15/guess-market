import javafx.application.Application;
public class WalkLauncher {
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        Class<? extends Application> type =
                (Class<? extends Application>) Class.forName(args[0]);
        Application.launch(type, java.util.Arrays.copyOfRange(args, 1, args.length));
    }
}