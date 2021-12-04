import java.util.List;
import java.util.Map;

public interface ProcessService {

    public List<Map<String, String>> textMapping();

    public void executeManufacture(InputInfo inputInfo) throws Exception;

    public boolean viewContactSwitch();

    public void controlPump(InputInfo inputInfo) throws Exception;

    public void controlLED(boolean status);

    public int measureProductWeight();
}
