/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.mining.core.category;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

/**
 * Manages the set of commit evaluation categories.
 *
 * <p>Loads initial categories from the bundled {@code categories.json}
 * resource and supports dynamic addition of new categories discovered
 * during Gemini evaluation.</p>
 */
public class CategoryManager {

	private final List<String> categories;
	private final Gson gson;

	/**
	 * Creates a CategoryManager and loads initial categories from resources.
	 */
	public CategoryManager() {
		this.gson = new GsonBuilder().setPrettyPrinting().create();
		this.categories = new ArrayList<>(loadInitialCategories());
	}

	/**
	 * Creates a CategoryManager with the given categories.
	 *
	 * @param categories initial category list
	 */
	public CategoryManager(List<String> categories) {
		this.gson = new GsonBuilder().setPrettyPrinting().create();
		this.categories = new ArrayList<>(categories);
	}

	/**
	 * Adds a new category if it doesn't already exist.
	 *
	 * @param category the category to add
	 * @return true if the category was added
	 */
	public boolean addCategory(String category) {
		if (category == null || category.isBlank()) {
			return false;
		}
		String normalized = category.trim();
		if (categories.stream().anyMatch(c -> c.equalsIgnoreCase(normalized))) {
			return false;
		}
		categories.add(normalized);
		return true;
	}

	/**
	 * Returns all categories.
	 *
	 * @return unmodifiable list of categories
	 */
	public List<String> getCategories() {
		return List.copyOf(categories);
	}

	/**
	 * Returns the categories formatted as a JSON array string.
	 *
	 * @return JSON array of categories
	 */
	public String getCategoriesJson() {
		return gson.toJson(categories);
	}

	private static List<String> loadInitialCategories() {
		try (InputStream is = CategoryManager.class.getResourceAsStream("/categories.json")) {
			if (is == null) {
				return getDefaultCategories();
			}
			String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
			List<String> loaded = new Gson().fromJson(json, new TypeToken<List<String>>() {}.getType());
			return loaded != null ? loaded : getDefaultCategories();
		} catch (IOException e) {
			return getDefaultCategories();
		}
	}

	private static List<String> getDefaultCategories() {
		return List.of(
				"Collections",
				"Java-Modernization",
				"Performance",
				"Null-Safety",
				"JUnit5-Migration",
				"Try-with-Resources",
				"Lambda-Simplification",
				"Encoding",
				"String-API");
	}
}
