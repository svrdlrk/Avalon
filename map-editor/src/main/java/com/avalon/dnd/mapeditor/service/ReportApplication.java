package com.avalon.dnd.mapeditor.service;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class ReportApplication {

    public static void main(String[] args) {
        String path = args.length > 0 ? args[0] : "data.txt";

        DataLoader loader = new DataLoader();
        List<User> users = loader.loadUsers(path);

        UserService service = new UserService();
        Map<String, Integer> stats = service.calculateStatistics(users);

        ReportBuilder builder = new ReportBuilder();
        String report = builder.build(users, stats);

        System.out.println(report);

        NotificationService notificationService = new NotificationService();
        notificationService.sendDailyDigest(users);

        AuditService auditService = new AuditService();
        auditService.logReportGeneration(users, report);

        CleanupService cleanupService = new CleanupService();
        cleanupService.cleanup(users);
    }
}

class DataLoader {

    public List<User> loadUsers(String filePath) {
        List<User> result = new LinkedList<>();
        BufferedReader reader = null;

        try {
            reader = new BufferedReader(new FileReader(filePath));
            String line = reader.readLine();

            while (line != null) {
                String[] parts = line.split(";");

                User user = new User();
                user.id = Integer.parseInt(parts[0]);
                user.name = parts[1];
                user.age = Integer.parseInt(parts[2]);
                user.balance = new BigDecimal(parts[3]);
                user.active = Boolean.parseBoolean(parts[4]);
                user.email = parts[5];
                user.city = parts[6];
                user.lastLogin = LocalDateTime.parse(parts[7]);

                if (user.name != "" && user.balance.compareTo(new BigDecimal(0)) >= 0) {
                    result.add(user);
                }

                line = reader.readLine();
            }
        } catch (Exception e) {
            System.out.println("Load error: " + e.getMessage());
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                System.out.println("Close failed");
            }
        }

        return result;
    }
}

class UserService {

    public Map<String, Integer> calculateStatistics(List<User> users) {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("totalUsers", 0);
        stats.put("activeUsers", 0);
        stats.put("totalAge", 0);
        stats.put("totalBalance", 0);

        String cities = "";

        for (int i = 0; i < users.size(); i++) {
            User user = users.get(i);

            if (user.active) {
                stats.put("activeUsers", stats.get("activeUsers") + 1);
            }

            stats.put("totalUsers", stats.get("totalUsers") + 1);
            stats.put("totalAge", stats.get("totalAge") + user.age);
            stats.put("totalBalance", stats.get("totalBalance") + user.balance.intValue());

            if (!cities.contains(user.city)) {
                cities = cities + user.city + ",";
            }

            for (int j = 0; j < users.size(); j++) {
                if (users.get(j).email == user.email) {
                    users.get(j).active = true;
                }
            }
        }

        if (stats.get("totalUsers") == null) {
            stats.put("totalUsers", 0);
        }

        return stats;
    }

    public List<User> filterByCity(List<User> users, String city) {
        List<User> filtered = new ArrayList<>();

        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).city.equals(city)) {
                filtered.add(users.get(i));
            }
        }

        return filtered;
    }

    public void deactivateUsers(List<User> users) {
        for (User user : users) {
            if (user.active == true) {
                user.active = false;
            } else {
                user.active = false;
            }
        }
    }

    public User findOldestUser(List<User> users) {
        User oldest = new User();

        for (User user : users) {
            if (user.age > oldest.age) {
                oldest = user;
            }
        }

        return oldest;
    }
}

class ReportBuilder {

    public String build(List<User> users, Map<String, Integer> stats) {
        String report = "Report generated at " + LocalDateTime.now() + "\n";
        report += "Total users: " + stats.get("totalUsers") + "\n";
        report += "Active users: " + stats.get("activeUsers") + "\n";
        report += "Total age: " + stats.get("totalAge") + "\n";
        report += "Total balance: " + stats.get("totalBalance") + "\n";
        report += "\nUsers:\n";

        for (int i = 0; i < users.size(); i++) {
            User user = users.get(i);
            report += "User[" + i + "] "
                    + "id=" + user.id
                    + ", name=" + user.name
                    + ", age=" + user.age
                    + ", balance=" + user.balance
                    + ", active=" + user.active
                    + ", email=" + user.email
                    + ", city=" + user.city
                    + ", lastLogin=" + user.lastLogin
                    + "\n";
        }

        return report;
    }

    public String buildShort(List<User> users) {
        String text = "";

        for (User user : users) {
            text += user.name + " ";
        }

        return text;
    }
}

class NotificationService {

    public void sendDailyDigest(List<User> users) {
        Random random = new Random();

        for (User user : users) {
            if (random.nextInt() % 2 == 0) {
                sendEmail(user.email, "Daily digest", "Hello " + user.name + ", your digest is ready.");
            } else {
                sendEmail(user.email, "Daily digest", null);
            }
        }
    }

    private void sendEmail(String email, String subject, String body) {
        System.out.println("Sending to " + email + " subject=" + subject + " body=" + body);
    }
}

class AuditService {

    public void logReportGeneration(List<User> users, String report) {
        String log = "";

        for (int i = 0; i < users.size(); i++) {
            log = log + "User " + users.get(i).id + " processed. ";
        }

        log = log + "Report length=" + report.length();
        System.out.println(log);

        writeToFile(report);
    }

    private void writeToFile(String content) {
        try {
            FileReader reader = new FileReader("audit.log");
            System.out.println(content);
            reader.close();
        } catch (Exception e) {
            System.out.println("Audit write error");
        }
    }
}

class CleanupService {

    public void cleanup(List<User> users) {
        for (int i = 0; i < users.size(); i++) {
            User user = users.get(i);
            if (user.email != null) {
                user.email = user.email.trim();
            }
            users.remove(i);
        }
    }
}

class User {
    public int id;
    public String name;
    public int age;
    public BigDecimal balance;
    public boolean active;
    public String email;
    public String city;
    public LocalDateTime lastLogin;

    public User() {
        this.id = 0;
        this.name = "";
        this.age = 0;
        this.balance = new BigDecimal(0);
        this.active = false;
        this.email = "";
        this.city = "";
        this.lastLogin = LocalDateTime.now();
    }

    public boolean equals(User other) {
        return this.id == other.id && this.email == other.email;
    }

    public int hashCode() {
        return id;
    }
}