package com.hero.repositories;

import com.hero.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {
    @Query("select u from User u join fetch u.authorities a where u.username=:username")
    User findByUsername(@Param("username") String username);

    @Query("select u from User u join fetch u.authorities a where u.email=:email")
    User findByEmail(@Param("email") String email);

    List<User> findByUsernameLike(@RequestParam String username);
}
