package com.capaciti.ops.config;

import com.capaciti.ops.ai.ClassificationResult;
import com.capaciti.ops.ai.KeywordTicketClassifier;
import com.capaciti.ops.ticket.Category;
import com.capaciti.ops.ticket.Ticket;
import com.capaciti.ops.ticket.TicketRepository;
import com.capaciti.ops.ticket.TicketStatus;
import com.capaciti.ops.user.Role;
import com.capaciti.ops.user.User;
import com.capaciti.ops.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * DEV ONLY. Creates demo users and ~80 demo tickets spread over the last 28 days so the dashboard, forecast and
 * reports have something to show. Disable with app.seed.enabled=false. All demo passwords are "Passw0rd!".
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final String DEMO_PASSWORD = "Passw0rd!";

    private static final Map<Category, List<String[]>> SAMPLES = Map.of(
            Category.IT, List.of(
                    new String[]{"Cannot log in to my laptop", "My password was reset but I still cannot login to my laptop."},
                    new String[]{"VPN not working from home", "The VPN keeps disconnecting and I cannot access the server."},
                    new String[]{"Printer on 3rd floor offline", "The office printer shows an error and will not print."},
                    new String[]{"URGENT: email down", "Outlook email is down for the whole team, we cannot work."}),
            Category.HR, List.of(
                    new String[]{"Annual leave balance question", "How do I check my annual leave balance? Question about policy."},
                    new String[]{"Payslip missing for last month", "I did not receive my payslip and need it urgently for a loan."},
                    new String[]{"Onboarding documents for new hire", "Please send the onboarding contract and benefits information."}),
            Category.FINANCE, List.of(
                    new String[]{"Expense reimbursement not paid", "My expense claim from last month is still unpaid."},
                    new String[]{"Invoice query from supplier", "Supplier invoice 4471 does not match the purchase order."},
                    new String[]{"Budget approval for training", "Need approval for the quarterly training budget."}),
            Category.OPERATIONS, List.of(
                    new String[]{"Meeting room booking clash", "Two teams booked the same meeting room on Friday."},
                    new String[]{"Warehouse stock count mismatch", "Inventory in the warehouse does not match the system."},
                    new String[]{"Delivery from supplier delayed", "Our shipment from the supplier has not arrived."},
                    new String[]{"Office cleaning schedule", "Question about the cleaning schedule for next week."}));

    private final UserRepository users;
    private final TicketRepository tickets;
    private final PasswordEncoder encoder;
    private final KeywordTicketClassifier classifier;
    private final boolean demoTickets;

    public DataSeeder(UserRepository users, TicketRepository tickets, PasswordEncoder encoder,
                      KeywordTicketClassifier classifier, @Value("${app.seed.demo-tickets:true}") boolean demoTickets) {
        this.users = users;
        this.tickets = tickets;
        this.encoder = encoder;
        this.classifier = classifier;
        this.demoTickets = demoTickets;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (users.count() > 0) return;
        String hash = encoder.encode(DEMO_PASSWORD);

        create("admin@ops.local", "Alex Admin", Role.ADMIN, null, hash);
        create("manager@ops.local", "Maya Manager", Role.MANAGER, null, hash);
        List<User> agents = new ArrayList<>();
        agents.add(create("agent.it@ops.local", "Ian IT-Agent", Role.AGENT, Category.IT, hash));
        agents.add(create("agent.hr@ops.local", "Hana HR-Agent", Role.AGENT, Category.HR, hash));
        agents.add(create("agent.finance@ops.local", "Fiona Finance-Agent", Role.AGENT, Category.FINANCE, hash));
        agents.add(create("agent.ops@ops.local", "Olu Ops-Agent", Role.AGENT, Category.OPERATIONS, hash));
        User employee = create("employee@ops.local", "Emma Employee", Role.EMPLOYEE, null, hash);

        log.info("Seeded demo users (password '{}'): admin@, manager@, agent.it@, agent.hr@, agent.finance@, "
                + "agent.ops@, employee@ ops.local", DEMO_PASSWORD);
        if (!demoTickets) return;

        Random rnd = new Random(42);
        Instant now = Instant.now();
        for (int i = 0; i < 80; i++) {
            Category cat = Category.values()[rnd.nextInt(Category.values().length)];
            List<String[]> options = SAMPLES.get(cat);
            String[] s = options.get(rnd.nextInt(options.size()));
            ClassificationResult c = classifier.classify(s[0], s[1]);

            // slightly more tickets in recent days so the trend is not flat
            double ageDays = 28 * Math.pow(rnd.nextDouble(), 1.4);
            Instant created = now.minus(Duration.ofMinutes((long) (ageDays * 24 * 60)));

            Ticket t = new Ticket();
            t.setTitle(s[0]);
            t.setDescription(s[1]);
            t.setRequester(employee);
            t.setCategory(c.category());
            t.setPriority(c.priority());
            t.setClassificationConfidence(c.confidence());
            t.setClassificationSource(rnd.nextInt(12) == 0 ? "MANUAL" : c.source());
            t.setCreatedAt(created);
            t.setUpdatedAt(created);
            User agent = agents.stream().filter(a -> a.getDepartment() == c.category()).findFirst().orElse(null);
            t.setAssignee(agent);

            boolean old = ageDays > 3;
            if (old && rnd.nextInt(10) < 8) {
                t.setStatus(TicketStatus.RESOLVED);
                t.setFirstResponseAt(created.plus(Duration.ofMinutes(30 + rnd.nextInt(600))));
                t.setResolvedAt(created.plus(Duration.ofHours(4 + rnd.nextInt(60))));
                t.setUpdatedAt(t.getResolvedAt());
            } else if (rnd.nextBoolean()) {
                t.setStatus(TicketStatus.IN_PROGRESS);
                t.setFirstResponseAt(created.plus(Duration.ofMinutes(20 + rnd.nextInt(300))));
            } else {
                t.setStatus(TicketStatus.ASSIGNED);
            }
            // seeded history should not trigger SLA escalation emails on startup
            t.setEscalated(old);
            tickets.save(t);
        }
        log.info("Seeded 80 demo tickets");
    }

    private User create(String email, String name, Role role, Category dept, String hash) {
        User u = new User();
        u.setEmail(email);
        u.setFullName(name);
        u.setRole(role);
        u.setDepartment(dept);
        u.setPasswordHash(hash);
        return users.save(u);
    }
}
