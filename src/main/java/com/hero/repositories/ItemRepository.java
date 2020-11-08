package com.hero.repositories;

import com.hero.entities.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.web.bind.annotation.RequestParam;

import javax.persistence.Id;
import java.util.List;

public interface ItemRepository extends JpaRepository<Item, Long> {

    List<Item> findByNameLike(@RequestParam String name);

    List<Item> findByCodeLike(@RequestParam String code);

    @Query("select item from Item item where lower(item.name) like :searchInput or lower(item.code) like :searchInput")
    List<Item> findByNameOrCodeLike(@RequestParam String searchInput);
}

