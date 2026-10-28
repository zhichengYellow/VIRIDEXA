import com.sun.tools.attach.VirtualMachine;
import javax.management.*;
import javax.management.openmbean.CompositeData;
import javax.management.remote.*;
import java.util.*;
/** Local attach only. Never opens a remotely accessible management port. */
class JvmSampler {
 public static void main(String[] args)throws Exception{
  var vm=VirtualMachine.attach(args[0]);String address=vm.startLocalManagementAgent();vm.detach();
  try(var connector=JMXConnectorFactory.connect(new JMXServiceURL(address))){var c=connector.getMBeanServerConnection();
   for(int i=0;i<Integer.parseInt(args[1]);i++){
    var heap=(CompositeData)c.getAttribute(new ObjectName("java.lang:type=Memory"),"HeapMemoryUsage");
    long gcCount=0,gcMs=0;for(var name:c.queryNames(new ObjectName("java.lang:type=GarbageCollector,*"),null)){gcCount+=(Long)c.getAttribute(name,"CollectionCount");gcMs+=(Long)c.getAttribute(name,"CollectionTime");}
    var pools=new ArrayList<String>();for(var name:c.queryNames(new ObjectName("com.zaxxer.hikari:type=Pool (*)"),null)){pools.add("{\"active\":"+c.getAttribute(name,"ActiveConnections")+",\"idle\":"+c.getAttribute(name,"IdleConnections")+",\"total\":"+c.getAttribute(name,"TotalConnections")+",\"awaiting\":"+c.getAttribute(name,"ThreadsAwaitingConnection")+"}");}
    Object cpu=c.getAttribute(new ObjectName("java.lang:type=OperatingSystem"),"ProcessCpuLoad");
    System.out.println("{\"time\":"+System.currentTimeMillis()+",\"heapUsed\":"+heap.get("used")+",\"heapMax\":"+heap.get("max")+",\"gcCount\":"+gcCount+",\"gcMs\":"+gcMs+",\"processCpuLoad\":"+cpu+",\"pools\":["+String.join(",",pools)+"]}");
    System.out.flush();Thread.sleep(500);
   }
  }
 }
}
