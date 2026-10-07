package br.com.planned.api.repository;

import java.util.UUID;

/** One row of {@link TaskRepository#findAncestors}: an ancestor's id and title. */
public interface TaskAncestor {

	UUID getId();

	String getTitle();
}
