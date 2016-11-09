import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.security.KeyStore;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

public class SimpleHandler
{
    public static void main(String[] args) throws Exception
    {
        MBeanContainer mBeanContainer = new MBeanContainer(ManagementFactory.getPlatformMBeanServer());
        
        // System.setProperty(ManagedSelector.class.getName() + "$SelectorProducer.ExecutionStrategy", ProduceExecuteConsume.class.getName());
        
        Server server = new Server(8080);
        QueuedThreadPool qtp = (QueuedThreadPool) server.getThreadPool();
        qtp.setMaxThreads(20);
        qtp.setDetailedDump(true);
        
        SslContextFactory ctx = getSslContextFactory();
        HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.addCustomizer(new SecureRequestCustomizer());
        ServerConnector https = new ServerConnector(server, new SslConnectionFactory(ctx, HttpVersion.HTTP_1_1.asString()),
                                                   new HttpConnectionFactory(httpConfig));
        https.setPort(8443);
        https.setIdleTimeout(300000);
        server.addConnector(https);

        server.setHandler(new HelloHandler());

        server.start();
        mBeanContainer.beanAdded(null, server);
        System.out.println("running server");
        server.join();
    }

    private static SslContextFactory getSslContextFactory() {
        KeyStore ks = getStore("keystore.jks");
        KeyStore ts = getStore("keystore.jks");
        SslContextFactory ctx = new SslContextFactory();
        ctx.setKeyStore(ks);
        ctx.setKeyStorePassword("password");
        ctx.setCertAlias("1");
        ctx.setTrustStore(ts);
        ctx.setTrustStorePassword("password");
//        ctx.setWantClientAuth(true);
//        ctx.setIncludeProtocols("TLSv1", "TLSv1.1", "TLSv1.2");
//        ctx.setExcludeCipherSuites("(?!TLS_RSA_WITH_AES_256_CBC_SHA)(?!TLS_RSA_WITH_AES_128_CBC_SHA).*");
//        ctx.setIncludeCipherSuites("TLS_RSA_WITH_AES_256_CBC_SHA",
//                                   "TLS_RSA_WITH_AES_128_CBC_SHA");
        return ctx;
    }

    private static KeyStore getStore(String path) {
        KeyStore ks;
        try {
            ks = KeyStore.getInstance("JKS");
        } catch (Exception e) {
            return null;
        }
        try (FileInputStream fis = new FileInputStream(path)) {
            char[] password = "password".toCharArray();
            ks.load(fis, password);
            return ks;
        } catch (Exception e) {
            System.out.println(e);
            return null;
        }
    }

    static class HelloHandler extends AbstractHandler
    {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(8);
        
        public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException
        {
            BufferedReader reader = request.getReader();
            read_data(baseRequest, reader);
            response.setHeader("Server", "HelloWorld");
            response.setContentType("application/json");
            response.setStatus(200);
            baseRequest.setHandled(true);
            response.getWriter().println("hello");
        }
    
        private void read_data(Request request, BufferedReader reader) {
            char[] data = new char[16 * 1024];
            AtomicLong total = new AtomicLong(0);
    
            Runnable runnable = () ->
            {
                EndPoint ep = request.getHttpChannel().getEndPoint();
                System.out.printf("total = %,d%n", total.get());
                System.out.println("EndPoint = " + ep);
                System.out.println("Connection = " + ep.getConnection());
            };
            
            try{
                while(true)
                {
                    ScheduledFuture<?> task = scheduler.schedule(runnable, 5, TimeUnit.SECONDS);
                    int justRead = reader.read(data, 0, data.length);
                    task.cancel(false);
                    if(justRead < 0) break;
                    total.addAndGet(justRead);
                }
                
                long expected = 100 * 1024;
                if (total.get() != expected)
                {
                    System.out.printf("%s | Did not read expected %,d bytes: read %,d bytes%n", request, expected, total.get());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
