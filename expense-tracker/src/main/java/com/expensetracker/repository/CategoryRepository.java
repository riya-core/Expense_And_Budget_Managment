package com.expensetracker.repository;

import com.expensetracker.entity.Category;
import com.expensetracker.entity.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    /** Default categories (user is null) plus this user's own. */
    @Query("select c from Category c left join c.user u " +
           "where u is null or u.id = :userId order by c.name")
    List<Category> findVisible(@Param("userId") Long userId);

    @Query("select c from Category c left join c.user u " +
           "where (u is null or u.id = :userId) and c.type = :type order by c.name")
    List<Category> findVisibleByType(@Param("userId") Long userId, @Param("type") TransactionType type);

    /** A category this user may use: a default one or their own. */
    @Query("select c from Category c left join c.user u " +
           "where c.id = :id and (u is null or u.id = :userId)")
    Optional<Category> findVisibleById(@Param("id") Long id, @Param("userId") Long userId);

    /** Only categories the user owns (defaults excluded), for edit and delete. */
    Optional<Category> findByIdAndUserId(Long id, Long userId);
}
