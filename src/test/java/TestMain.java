import UDisk.PluginMain;

public class TestMain {
    public static void main(String[] args) {
        PluginMain plugin = new PluginMain();
        plugin.loadPlugin();
        plugin.textChanged("geek");
        String ret;
        while (true) {
            while ((ret = plugin.pollFromResultQueue()) != null) {
                System.out.println(ret);
            }
        }
    }
}
