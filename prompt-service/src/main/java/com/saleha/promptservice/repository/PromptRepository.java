package com.saleha.promptservice.repository;

import com.saleha.promptservice.entity.Prompt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PromptRepository extends JpaRepository<Prompt, UUID> {

}