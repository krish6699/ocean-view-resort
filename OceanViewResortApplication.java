package com.oceanview;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.cors.*;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.security.Key;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

// ============================================================
// MAIN APPLICATION
// ============================================================
@SpringBootApplication
public class OceanViewResortApplication {
    public static void main(String[] args) {
        SpringApplication.run(OceanViewResortApplication.class, args);
    }
}

// ============================================================
// MODELS
// ============================================================

@Entity
@Table(name = "reservations")
@Data
@NoArgsConstructor
@AllArgsConstructor
class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String reservationNumber;

    @NotBlank(message = "Guest name is required")
    @Column(nullable = false)
    private String guestName;

    @NotBlank(message = "Address is required")
    @Column(nullable = false)
    private String address;

    @NotBlank(message = "Contact number is required")
    @Column(nullable = false)
    private String contactNumber;

    @NotBlank(message = "NIC or Passport is required")
    @Column(nullable = false)
    private String nicOrPassport;

    @NotNull(message = "Room type is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoomType roomType;

    @NotNull(message = "Check-in date is required")
    @Column(nullable = false)
    private LocalDate checkInDate;

    @NotNull(message = "Check-out date is required")
    @Column(nullable = false)
    private LocalDate checkOutDate;

    @Column(length = 500)
    private String specialRequests;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status = ReservationStatus.CONFIRMED;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum RoomType {
        STANDARD(15000.0), DELUXE(25000.0), PRESIDENTIAL(45000.0);
        private final double ratePerNight;
        RoomType(double r) { this.ratePerNight = r; }
        public double getRatePerNight() { return ratePerNight; }
    }

    public enum ReservationStatus {
        CONFIRMED, CHECKED_IN, CHECKED_OUT, CANCELLED
    }
}

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role = UserRole.STAFF;

    @Column(nullable = false)
    private String fullName;

    public AppUser(String username, String password, String fullName, UserRole role) {
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        this.role = role;
    }

    public enum UserRole { ADMIN, STAFF }
}

// ============================================================
// DTOs
// ============================================================

@Data
class ReservationRequest {
    @NotBlank private String guestName;
    @NotBlank private String address;
    @NotBlank private String contactNumber;
    @NotBlank private String nicOrPassport;
    @NotNull  private Reservation.RoomType roomType;
    @NotNull  private LocalDate checkInDate;
    @NotNull  private LocalDate checkOutDate;
    private String specialRequests;
}

@Data
class ReservationResponse {
    private Long id;
    private String reservationNumber;
    private String guestName;
    private String address;
    private String contactNumber;
    private String nicOrPassport;
    private Reservation.RoomType roomType;
    private double ratePerNight;
    private LocalDate checkInDate;
    private LocalDate checkOutDate;
    private long numberOfNights;
    private String specialRequests;
    private Reservation.ReservationStatus status;
    private String createdAt;
}

@Data
class BillResponse {
    private String reservationNumber;
    private String guestName;
    private String roomType;
    private double ratePerNight;
    private LocalDate checkInDate;
    private LocalDate checkOutDate;
    private long numberOfNights;
    private double subtotal;
    private double taxAmount;
    private double totalAmount;
    private String generatedAt;
}

@Data
class LoginRequest {
    private String username;
    private String password;
}

@Data
@AllArgsConstructor
class LoginResponse {
    private String token;
    private String username;
    private String fullName;
    private String role;
    private String message;
}

// ============================================================
// REPOSITORIES
// ============================================================

interface ReservationRepository extends JpaRepository<Reservation, Long> {
    Optional<Reservation> findByReservationNumber(String reservationNumber);
    List<Reservation> findByGuestNameContainingIgnoreCase(String guestName);
    List<Reservation> findByCheckInDate(LocalDate date);
    List<Reservation> findByCheckOutDate(LocalDate date);
}

interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);
    boolean existsByUsername(String username);
}

// ============================================================
// JWT SERVICE
// ============================================================

@Service
class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String generateToken(String username, String role) {
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    public String extractUsername(String token) {
        return getClaims(token).getSubject();
    }

    public boolean isTokenValid(String token) {
        try { getClaims(token); return true; }
        catch (Exception e) { return false; }
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith((javax.crypto.SecretKey) getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}

// ============================================================
// RESERVATION SERVICE
// ============================================================

@Service
@Transactional
class ReservationService {

    private static final double TAX_RATE = 0.15;

    @Autowired
    private ReservationRepository reservationRepository;

    public ReservationResponse createReservation(ReservationRequest request) {
        if (!request.getCheckOutDate().isAfter(request.getCheckInDate()))
            throw new IllegalArgumentException("Check-out date must be after check-in date");

        Reservation r = new Reservation();
        r.setReservationNumber(generateReservationNumber());
        r.setGuestName(request.getGuestName());
        r.setAddress(request.getAddress());
        r.setContactNumber(request.getContactNumber());
        r.setNicOrPassport(request.getNicOrPassport());
        r.setRoomType(request.getRoomType());
        r.setCheckInDate(request.getCheckInDate());
        r.setCheckOutDate(request.getCheckOutDate());
        r.setSpecialRequests(request.getSpecialRequests());
        r.setStatus(Reservation.ReservationStatus.CONFIRMED);
        return mapToResponse(reservationRepository.save(r));
    }

    public ReservationResponse getByReservationNumber(String num) {
        return mapToResponse(reservationRepository.findByReservationNumber(num)
                .orElseThrow(() -> new RuntimeException("Reservation not found: " + num)));
    }

    public List<ReservationResponse> getAllReservations() {
        return reservationRepository.findAll().stream()
                .map(this::mapToResponse).collect(Collectors.toList());
    }

    public List<ReservationResponse> searchByGuestName(String name) {
        return reservationRepository.findByGuestNameContainingIgnoreCase(name)
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public ReservationResponse cancelReservation(String num) {
        Reservation r = reservationRepository.findByReservationNumber(num)
                .orElseThrow(() -> new RuntimeException("Reservation not found: " + num));
        r.setStatus(Reservation.ReservationStatus.CANCELLED);
        return mapToResponse(reservationRepository.save(r));
    }

    public BillResponse generateBill(String num) {
        Reservation r = reservationRepository.findByReservationNumber(num)
                .orElseThrow(() -> new RuntimeException("Reservation not found: " + num));
        long nights = Math.max(1, ChronoUnit.DAYS.between(r.getCheckInDate(), r.getCheckOutDate()));
        double rate = r.getRoomType().getRatePerNight();
        double subtotal = nights * rate;
        double tax = subtotal * TAX_RATE;
        BillResponse bill = new BillResponse();
        bill.setReservationNumber(r.getReservationNumber());
        bill.setGuestName(r.getGuestName());
        bill.setRoomType(r.getRoomType().name());
        bill.setRatePerNight(rate);
        bill.setCheckInDate(r.getCheckInDate());
        bill.setCheckOutDate(r.getCheckOutDate());
        bill.setNumberOfNights(nights);
        bill.setSubtotal(subtotal);
        bill.setTaxAmount(tax);
        bill.setTotalAmount(subtotal + tax);
        bill.setGeneratedAt(LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd MMMM yyyy, HH:mm")));
        return bill;
    }

    private String generateReservationNumber() {
        return String.format("OVR-%03d", reservationRepository.count() + 1);
    }

    private ReservationResponse mapToResponse(Reservation r) {
        ReservationResponse dto = new ReservationResponse();
        dto.setId(r.getId());
        dto.setReservationNumber(r.getReservationNumber());
        dto.setGuestName(r.getGuestName());
        dto.setAddress(r.getAddress());
        dto.setContactNumber(r.getContactNumber());
        dto.setNicOrPassport(r.getNicOrPassport());
        dto.setRoomType(r.getRoomType());
        dto.setRatePerNight(r.getRoomType().getRatePerNight());
        dto.setCheckInDate(r.getCheckInDate());
        dto.setCheckOutDate(r.getCheckOutDate());
        dto.setNumberOfNights(ChronoUnit.DAYS.between(r.getCheckInDate(), r.getCheckOutDate()));
        dto.setSpecialRequests(r.getSpecialRequests());
        dto.setStatus(r.getStatus());
        dto.setCreatedAt(r.getCreatedAt() != null
                ? r.getCreatedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")) : "");
        return dto;
    }
}

// ============================================================
// SECURITY CONFIG
// ============================================================

@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Autowired private JwtService jwtService;
    @Autowired private UserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(c -> c.disable())
            .cors(c -> c.configurationSource(corsConfigurationSource()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/auth/**", "/api/health").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public OncePerRequestFilter jwtAuthFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                    FilterChain chain) throws ServletException, IOException {
                String header = req.getHeader("Authorization");
                if (header != null && header.startsWith("Bearer ")) {
                    String token = header.substring(7);
                    if (jwtService.isTokenValid(token)) {
                        String username = jwtService.extractUsername(token);
                        UserDetails ud = userDetailsService.loadUserByUsername(username);
                        SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(ud, null, ud.getAuthorities()));
                    }
                }
                chain.doFilter(req, res);
            }
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration c) throws Exception {
        return c.getAuthenticationManager();
    }
}

// ============================================================
// DATA SEEDER (creates default users)
// ============================================================

@Component
class DataSeeder implements CommandLineRunner, UserDetailsService {

    @Autowired private AppUserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (!userRepository.existsByUsername("admin")) {
            userRepository.save(new AppUser("admin",
                passwordEncoder.encode("ocean123"), "Administrator", AppUser.UserRole.ADMIN));
            System.out.println("✅ Default admin created: admin / ocean123");
        }
        if (!userRepository.existsByUsername("staff")) {
            userRepository.save(new AppUser("staff",
                passwordEncoder.encode("staff123"), "Front Desk Staff", AppUser.UserRole.STAFF));
        }
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        return User.withUsername(user.getUsername())
                .password(user.getPassword())
                .roles(user.getRole().name())
                .build();
    }
}

// ============================================================
// AUTH CONTROLLER
// ============================================================

@RestController
@RequestMapping("/api/auth")
class AuthController {

    @Autowired private AuthenticationManager authenticationManager;
    @Autowired private JwtService jwtService;
    @Autowired private AppUserRepository userRepository;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
            AppUser user = userRepository.findByUsername(request.getUsername()).orElseThrow();
            String token = jwtService.generateToken(user.getUsername(), user.getRole().name());
            return ResponseEntity.ok(new LoginResponse(token, user.getUsername(),
                    user.getFullName(), user.getRole().name(), "Login successful"));
        } catch (Exception e) {
            return ResponseEntity.status(401)
                    .body(new LoginResponse(null, null, null, null, "Invalid username or password"));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Ocean View Resort API is running!");
    }
}

// ============================================================
// RESERVATION CONTROLLER
// ============================================================

@RestController
@RequestMapping("/api/reservations")
class ReservationController {

    @Autowired private ReservationService reservationService;

    @PostMapping
    public ResponseEntity<ReservationResponse> create(@Valid @RequestBody ReservationRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reservationService.createReservation(req));
    }

    @GetMapping
    public ResponseEntity<List<ReservationResponse>> getAll() {
        return ResponseEntity.ok(reservationService.getAllReservations());
    }

    @GetMapping("/{num}")
    public ResponseEntity<ReservationResponse> getOne(@PathVariable String num) {
        return ResponseEntity.ok(reservationService.getByReservationNumber(num));
    }

    @GetMapping("/search")
    public ResponseEntity<List<ReservationResponse>> search(@RequestParam String name) {
        return ResponseEntity.ok(reservationService.searchByGuestName(name));
    }

    @GetMapping("/{num}/bill")
    public ResponseEntity<BillResponse> bill(@PathVariable String num) {
        return ResponseEntity.ok(reservationService.generateBill(num));
    }

    @DeleteMapping("/{num}")
    public ResponseEntity<ReservationResponse> cancel(@PathVariable String num) {
        return ResponseEntity.ok(reservationService.cancelReservation(num));
    }
}

// ============================================================
// GLOBAL EXCEPTION HANDLER
// ============================================================

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(e ->
            errors.put(((FieldError) e).getField(), e.getDefaultMessage()));
        return ResponseEntity.badRequest().body(errors);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntime(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegal(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }
}
