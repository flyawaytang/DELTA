package org.deltaproject.manager.core;

import org.deltaproject.manager.testcase.TestAdvancedCase;
import org.deltaproject.manager.testcase.TestInfo;
import org.deltaproject.manager.testcase.TestSwitchCase;
import org.deltaproject.manager.utils.J2sshCient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Field;
import java.net.Socket;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeMap;

public class AttackConductor {
    private static final Logger log = LoggerFactory.getLogger(AttackConductor.class);

    private HashMap<String, String> infoSwitchCase;
    private HashMap<String, String> infoControllerCase;
    private HashMap<String, String> infoAdvancedCase;

    private AppAgentManager appm;
    private HostAgentManager hostm;
    private ChannelAgentManager channelm;
    private ControllerManager controllerm;

    private Configuration cfg;

    private DataOutputStream dos;
    private DataInputStream dis;

    private TestAdvancedCase testAdvancedCase;
    private TestSwitchCase testSwitchCase;

    public AttackConductor(String config) {
        infoControllerCase = new HashMap<String, String>();
        infoSwitchCase = new HashMap<String, String>();
        infoAdvancedCase = new HashMap<String, String>();

        cfg = new Configuration(config);

        this.controllerm = new ControllerManager(cfg);

        this.appm = new AppAgentManager();
        this.appm.setControllerType(cfg.getTargetController());

        this.hostm = new HostAgentManager();
        this.channelm = new ChannelAgentManager();

		/* Update Test Cases */
        TestInfo.updateAdvancedCase(infoAdvancedCase);
        TestInfo.updateControllerCase(infoControllerCase);
        TestInfo.updateSwitchCase(infoSwitchCase);

        testAdvancedCase = new TestAdvancedCase(appm, hostm, channelm, controllerm);
        testSwitchCase = new TestSwitchCase(cfg);
    }

    public String showConfig() {
        return cfg.show();
    }

    public void setSocket(Socket socket) throws IOException {
        dos = new DataOutputStream(socket.getOutputStream());
        dis = new DataInputStream(socket.getInputStream());

        String agentType = dis.readUTF();

        if (agentType.contains("AppAgent")) {
            appm.setAppSocket(socket, dos, dis);
        } else if (agentType.contains("ActAgent")) { /* for OpenDaylight */
            appm.setActSocket(socket, dos, dis);
        } else if (agentType.contains("ChannelAgent")) {
            channelm.setSocket(socket, dos, dis);

			/* send configuration to channel agent */
            String config = "config," + "version:" + cfg.getOFVer() + ",nic:" + cfg.getMitmNIC() + ",port:"
                    + cfg.getOFPort() + ",controller_ip:" + cfg.getControllerIP() + ",switch_ip:" + cfg.getSwitchIP(0)
                    + ",handler:dummy" + ",cbench:" + cfg.getCbenchRoot();

            channelm.write(config);

        } else if (agentType.contains("HostAgent")) {
            hostm.setSocket(socket, dos, dis);
            hostm.write("connected");
        }
    }

    public void replayKnownAttack(String code) {
        if (code.charAt(0) == '1')
            testSwitchCase.replayKnownAttack(code);
        if (code.charAt(0) == '3')
            testAdvancedCase.replayKnownAttack(code);
    }

    public void printAttackList() {
        System.out.println("\nControl Plane Test Set (under developping)");

        Iterator<String> treeMapIter = infoControllerCase.keySet().iterator();

        while (treeMapIter.hasNext()) {
            String key = treeMapIter.next();
            String value = infoControllerCase.get(key);
            System.out.println(String.format("%s\t: %s", key, value));
        }

        System.out.println("\nData Plane Test Set");
        TreeMap<String, String> treeMap = new TreeMap<String, String>(infoSwitchCase);
        treeMapIter = treeMap.keySet().iterator();
        while (treeMapIter.hasNext()) {

            String key = (String) treeMapIter.next();
            String value = (String) treeMap.get(key);
            System.out.println(String.format("%s\t: %s", key, value));
        }

        System.out.println("\nAdvanced Test Set");

        treeMap = new TreeMap<String, String>(infoAdvancedCase);
        treeMapIter = treeMap.keySet().iterator();
        while (treeMapIter.hasNext()) {

            String key = (String) treeMapIter.next();
            String value = (String) treeMap.get(key);
            System.out.println(String.format("%s\t: %s", key, value));
        }
    }

    public boolean isPossibleAttack(String code) {
        if (infoControllerCase.containsKey(code))
            return true;
        else if (infoSwitchCase.containsKey(code))
            return true;
        else if (infoAdvancedCase.containsKey(code))
            return true;
        else
            return false;
    }

    public void test(String code) {

    }

    public void replayAllKnownAttacks() {

    }
}
