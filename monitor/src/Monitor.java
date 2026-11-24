import net.jini.core.lookup.ServiceRegistrar;
import net.jini.core.lookup.ServiceTemplate;
import net.jini.core.discovery.LookupLocator;
import net.jini.space.JavaSpace;

/**
 * Observa o espaço de tuplas a cada 3 segundos e exibe quantas tarefas pendentes existem.
 *
 * O monitor NÃO deve remover tarefas do espaço — apenas observar.
 *
 * Nível 2B: localize o comentário TODO abaixo e complete a implementação.
 */
public class Monitor {

    private static final String REGGIE_HOST = "reggie";
    private static final int INTERVALO_SEGUNDOS = 3;
    private static final int MAX_ID = 100; // faixa de ids verificada a cada ciclo

    public static void main(String[] args) throws Exception {
        System.setProperty("java.security.policy", "security.policy");
        if (System.getSecurityManager() == null) {
            System.setSecurityManager(new SecurityManager());
        }

        JavaSpace space = descobrirEspaco();
        System.out.println("[MONITOR] Espaço encontrado. Iniciando monitoramento a cada "
            + INTERVALO_SEGUNDOS + "s...");

        while (true) {
            Thread.sleep(INTERVALO_SEGUNDOS * 1000);
            int pendentes = contarTarefas(space);
            System.out.println("[MONITOR] Tarefas pendentes no espaço: " + pendentes);
        }
    }

    private static int contarTarefas(JavaSpace space) throws Exception {
        int count = 0;

        // read() e nao take(): read() so copia a entrada, take() removeria a
        // tarefa do espaco e o monitor "roubaria" trabalho do consumidor.
        //
        // Nao existe count(), e ler varias vezes o mesmo template com NO_WAIT
        // devolve sempre a mesma entrada (testado: contava 1 o tempo todo).
        // Por isso o monitor faz um read() por id, com um template que fixa o
        // id e deixa os outros campos como curinga. Funciona porque os ids sao
        // inteiros de 1 a MAX_ID; tarefas fora dessa faixa nao seriam contadas.
        for (int id = 1; id <= MAX_ID; id++) {
            TaskEntry porId = new TaskEntry(id, null, null);
            TaskEntry encontrada = (TaskEntry) space.read(porId, null, JavaSpace.NO_WAIT);
            if (encontrada != null) {
                count++;
            }
        }

        return count;
    }

    private static JavaSpace descobrirEspaco() throws Exception {
        LookupLocator locator = new LookupLocator("jini://" + REGGIE_HOST);
        ServiceTemplate template = new ServiceTemplate(null, new Class[]{JavaSpace.class}, null);

        for (int tentativa = 1; tentativa <= 20; tentativa++) {
            try {
                ServiceRegistrar registrar = locator.getRegistrar();
                Object service = registrar.lookup(template);
                if (service != null) {
                    return (JavaSpace) service;
                }
            } catch (Exception e) {
                // reggie ou javaspaces ainda não prontos
            }
            System.out.println("[MONITOR] Aguardando espaço... (" + tentativa + "/20)");
            Thread.sleep(2000);
        }
        throw new RuntimeException("[MONITOR] Não foi possível encontrar o espaço de tuplas.");
    }
}
