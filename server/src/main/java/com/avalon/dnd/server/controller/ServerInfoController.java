package com.avalon.dnd.server.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

@RestController
public class ServerInfoController {

    @Value("${server.port:8080}")
    private int serverPort;

    @GetMapping("/api/server-info")
    public String getServerInfo() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isUp() && !ni.isLoopback()) {
                    Enumeration<InetAddress> addrs = ni.getInetAddresses();
                    while (addrs.hasMoreElements()) {
                        InetAddress addr = addrs.nextElement();
                        if (!addr.isLoopbackAddress() && addr.getAddress().length == 4) {
                            return "http://" + addr.getHostAddress() + ":" + serverPort;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return "http://localhost:" + serverPort;
    }
}
