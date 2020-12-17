package com.hero.services;

import com.google.common.base.Strings;
import com.hero.dtos.user.UserGetDto;
import com.hero.dtos.user.UserPostDto;
import com.hero.entities.EmailVerifier;
import com.hero.entities.User;
import com.hero.jwt.JwtConfig;
import com.hero.mappers.UserMapper;
import com.hero.repositories.AuthorityRepository;
import com.hero.repositories.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    @Value("${application.jwt.secretKey}")
    private String secretKey;

    @Value("${application.email.tokenExpirationAfterMinutes}")
    private int tokenExpirationAfterMinutes;

    private final JwtConfig jwtConfig;
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final AuthorityRepository authorityRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);
    private final int USERNAME_MIN_LENGTH = 6;
    private final int PASSWORD_MIN_LENGTH = 8;

    public void checkUsername(String username) {
        if (username == null || username.length() < USERNAME_MIN_LENGTH) {
            throw new RuntimeException("Username cannot be less than " + USERNAME_MIN_LENGTH + " characters");
        }

        if (userRepository.findByUsername(username) != null) { throw new RuntimeException("Username already exists"); }
    }

    public void checkEmail(String email) {
        if (email == null || email.length() == 0) {
            throw new RuntimeException("Please input a valid email address");
        }

        if (userRepository.findByEmail(email) != null) { throw new RuntimeException("This email has been used"); }
    }

    public void checkPassword(String password) {
        if (password == null || password.length() < PASSWORD_MIN_LENGTH) {
            throw new RuntimeException("Password cannot be less than " + PASSWORD_MIN_LENGTH + " characters");
        }
    }

    private Claims readJwsBody(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(jwtConfig.secretKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            throw new RuntimeException("Token expired");
        }
    }

    public User findUserById(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new RuntimeException("Cannot find the user"));
    }

    public UserGetDto getByHeaderWithToken(HttpHeaders headers) {
        List<String> authorizationHeaderList = headers.get(jwtConfig.getAuthorizationHeader());

        if (authorizationHeaderList == null || authorizationHeaderList.get(0) == null) {
            throw new RuntimeException("Invalid token");
        }

        String token = getTokenFromHeader(authorizationHeaderList.get(0));

        return getByToken(token);
    }

    public String getTokenFromHeader(String authorizationHeader) {
        if (Strings.isNullOrEmpty(authorizationHeader) || !authorizationHeader.startsWith(jwtConfig.getTokenPrefix())) {
            throw new RuntimeException("Invalid token");
        }

        String token = authorizationHeader.replace(jwtConfig.getTokenPrefix(), "");

        return token;
    }

    public UserGetDto getByToken(String token) {
        String username = readJwsBody(token).getSubject();
        return userMapper.fromEntity(userRepository.findByUsername(username));
    }

    public List<UserGetDto> getAll() {
        return userRepository.findAll().stream()
                .map((user -> userMapper.fromEntity(user)))
                .collect(Collectors.toList());
    }

    public UserGetDto getOne(Long id) {
        return userMapper.fromEntity(findUserById(id));
    }

    @Transactional
    public UserGetDto addOne(UserPostDto userPostDto) {
        //Long tokenExpirationAfterMinutes = 30L;
        String username = userPostDto.getUsername();
        String password = userPostDto.getPassword();
        String email = userPostDto.getEmail();

        checkUsername(username);
        checkEmail(email);
        checkPassword(password);

        User user = userMapper.toEntity(userPostDto);

        user.setEncodedPassword(passwordEncoder.encode(password));
        user.setStatus("unverified");
        user.setAuthorities(Set.of(authorityRepository.findByPermission("ROLE_TRAINEE")));
        User savedUser = userRepository.save(user);
        Long userId = savedUser.getId();

        String token = Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(Timestamp.valueOf(LocalDateTime.now().plusMinutes(tokenExpirationAfterMinutes)))
                .signWith(jwtConfig.secretKey())
                .compact();

        emailService.addEmailVerifier(userId, email, token);
        emailService.sendVerificationEmail(userId);

        return userMapper.fromEntity(savedUser);
    }

    @Transactional
    public void delete(Long id) {
        User user = findUserById(id);
        userRepository.delete(user);

        EmailVerifier emailVerifier = emailService.getEmailVerifierByUserId(id);
        if (emailVerifier != null) {
            emailService.deleteEmailVerifier(emailVerifier);
        }
    }

    @Transactional
    public UserGetDto verifyEmail(String token) {
        String username = readJwsBody(token).getSubject();
        User user = userRepository.findByUsername(username);

        //if (user.getStatus().equals("verified")) {
        //    return userMapper.fromEntity(user);
        //}

        EmailVerifier emailVerifier = emailService.getEmailVerifierByToken(token);

        if (emailVerifier == null) { throw new RuntimeException("Invalid token"); }

        user.setStatus("verified");
        user = userRepository.save(user);
        emailService.deleteEmailVerifier(emailVerifier);

        return userMapper.fromEntity(user);
    }
 }
