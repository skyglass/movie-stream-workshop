package com.ivanfranchin.moviesapi.userextra;

import com.ivanfranchin.moviesapi.userextra.model.UserExtra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserExtraRepository extends JpaRepository<UserExtra, String> {
}
