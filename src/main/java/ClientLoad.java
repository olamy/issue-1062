import java.lang.management.ManagementFactory;
import java.net.URI;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentProvider;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.BytesContentProvider;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.util.ssl.SslContextFactory;

public class ClientLoad implements Callable<Void>
{
    private static AtomicInteger failureCount = new AtomicInteger(0);
    private static AtomicInteger successCount = new AtomicInteger(0);
    private static AtomicInteger requestCount = new AtomicInteger(0);
    private static AtomicInteger responseCount = new AtomicInteger(0);
    
    public static void main(String args[]) throws Exception
    {
        int maxClients = 300;
    
        MBeanContainer mBeanContainer = new MBeanContainer(ManagementFactory.getPlatformMBeanServer());
        
        SslContextFactory sslContextFactory = new SslContextFactory(true);
        
        HttpClient client = new HttpClient(sslContextFactory);
        client.start();
        mBeanContainer.beanAdded(null, client);
        client.setMaxConnectionsPerDestination(maxClients);
        
        ExecutorService executor = Executors.newFixedThreadPool(maxClients);
        for (int i = 0; i < maxClients; i++)
        {
            executor.submit(new ClientLoad(client));
        }
        
        System.out.printf("Submitted %d client executions%n", maxClients);
        executor.shutdown();
        executor.awaitTermination(3, TimeUnit.MINUTES);
        System.out.println("Finished processing");
        
        System.out.println("Numbers:");
        System.out.printf("  Requests: %,d%n", requestCount.get());
        System.out.printf("  Responses: %,d%n", responseCount.get());
        System.out.printf("  Success: %,d%n", successCount.get());
        System.out.printf("  Failures: %,d%n", failureCount.get());
        
        System.exit(0);
    }
    
    private final HttpClient client;
    private final URI destURI;
    private final long duration;
    private Random rand;
    
    public ClientLoad(HttpClient client)
    {
        this.client = client;
        this.destURI = URI.create("https://localhost:8443/foo");
        this.duration = TimeUnit.SECONDS.toMillis(90); // 90 seconds
        this.rand = ThreadLocalRandom.current();
    }
    
    @Override
    public Void call() throws Exception
    {
        long expires = System.currentTimeMillis() + this.duration;
        while (System.currentTimeMillis() < expires)
        {
            Request request = client.newRequest(destURI);
            request.method(HttpMethod.PUT);
            request.content(genContent());
            
            try
            {
                requestCount.incrementAndGet();
                request.send();
                responseCount.incrementAndGet();
                successCount.incrementAndGet();
            }
            catch (Throwable t)
            {
                t.printStackTrace();
                failureCount.incrementAndGet();
            }
        }
        return null;
    }
    
    private ContentProvider genContent()
    {
        byte buf[] = new byte[100 * 1024];
        rand.nextBytes(buf);
        return new BytesContentProvider(buf);
    }
}
