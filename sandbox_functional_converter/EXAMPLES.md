# Functional Converter - Before & After Examples

This file contains real-world examples showing how the functional converter transforms imperative loops to functional streams while preserving comments.

## Example 1: Simple Filter with Comment

### Before Transformation
```java
public void processPositiveNumbers(List<Integer> numbers) {
    for (Integer num : numbers) {
        // Only process positive numbers
        if (num <= 0) continue;
        System.out.println(num);
    }
}
```

### After Transformation
```java
public void processPositiveNumbers(List<Integer> numbers) {
    numbers.stream()
        .filter(num -> {
            // Only process positive numbers
            return !(num <= 0);
        })
        .forEachOrdered(num -> {
            System.out.println(num);
        });
}
```

---

## Example 2: Filter + Map with Comments

### Before Transformation
```java
public void processNames(List<String> names) {
    for (String name : names) {
        // Skip empty names
        if (name == null || name.isEmpty()) continue;
        
        // Convert to uppercase for display
        String upper = name.toUpperCase();
        
        System.out.println(upper);
    }
}
```

### After Transformation
```java
public void processNames(List<String> names) {
    names.stream()
        .filter(name -> {
            // Skip empty names
            return !(name == null || name.isEmpty());
        })
        .map(name -> {
            // Convert to uppercase for display
            return name.toUpperCase();
        })
        .forEachOrdered(upper -> {
            System.out.println(upper);
        });
}
```

---

## Example 3: Complex Pipeline with Multiple Comments

### Before Transformation
```java
public void processCustomers(List<Customer> customers) {
    for (Customer customer : customers) {
        // Skip inactive accounts
        if (!customer.isActive()) continue;
        
        // Get the billing address
        Address addr = customer.getBillingAddress();
        
        // Skip customers without address
        if (addr == null) continue;
        
        // Format and print the address
        System.out.println(addr.format());
    }
}
```

### After Transformation
```java
public void processCustomers(List<Customer> customers) {
    customers.stream()
        .filter(customer -> {
            // Skip inactive accounts
            return !(!(customer.isActive()));
        })
        .map(customer -> {
            // Get the billing address
            return customer.getBillingAddress();
        })
        .filter(addr -> {
            // Skip customers without address
            return !(addr == null);
        })
        .forEachOrdered(addr -> {
            // Format and print the address
            System.out.println(addr.format());
        });
}
```

---

## Example 4: Reduction with Comment

### Before Transformation
```java
public int findMaximum(List<Integer> numbers) {
    int max = Integer.MIN_VALUE;
    for (Integer num : numbers) {
        // Track the maximum value
        max = Math.max(max, num);
    }
    return max;
}
```

### After Transformation
```java
public int findMaximum(List<Integer> numbers) {
    int max = Integer.MIN_VALUE;
    // Track the maximum value
    max = numbers.stream().reduce(max, Math::max);
    return max;
}
```

---

## Example 5: Complex Filter Conditions

### Before Transformation
```java
public void processValidUsers(List<User> users) {
    for (User user : users) {
        /* Check if user is active and verified */
        if (!user.isActive() || !user.isVerified()) continue;
        
        // Extract username for logging
        String username = user.getUsername();
        
        log.info("Processing: " + username);
    }
}
```

### After Transformation
```java
public void processValidUsers(List<User> users) {
    users.stream()
        .filter(user -> {
            /* Check if user is active and verified */
            return !(!user.isActive() || !user.isVerified());
        })
        .map(user -> {
            // Extract username for logging
            return user.getUsername();
        })
        .forEachOrdered(username -> {
            log.info("Processing: " + username);
        });
}
```

---

## Example 6: Accumulator Pattern

### Before Transformation
```java
public int countActiveItems(List<Item> items) {
    int count = 0;
    for (Item item : items) {
        // Count only active items
        if (item.isActive()) {
            count++;
        }
    }
    return count;
}
```

### After Transformation
```java
public int countActiveItems(List<Item> items) {
    // Count only active items
    int count = items.stream()
        .filter(item -> (item.isActive()))
        .map(_item -> 1)
        .reduce(0, Integer::sum);
    return count;
}
```

---

## Example 7: Multiple Variable Declarations

### Before Transformation
```java
public void processOrders(List<Order> orders) {
    for (Order order : orders) {
        // Calculate the total with tax
        double subtotal = order.getSubtotal();
        double tax = subtotal * 0.08;
        double total = subtotal + tax;
        
        System.out.println("Total: $" + total);
    }
}
```

### After Transformation
```java
public void processOrders(List<Order> orders) {
    orders.stream()
        .map(order -> {
            // Calculate the total with tax
            return order.getSubtotal();
        })
        .map(subtotal -> subtotal * 0.08)
        .map(tax -> {
            /* Note: This example shows limitations - 
               complex multi-variable calculations may need 
               manual refactoring for optimal stream usage */
            return subtotal + tax;  // May require adjustment
        })
        .forEachOrdered(total -> {
            System.out.println("Total: $" + total);
        });
}
```

> **Note**: Example 7 demonstrates current limitations. Complex calculations involving multiple variables may require manual adjustment for optimal stream transformation.

---

## Example 8: Bidirectional Transformation (Enhanced-For <-> While)

### Original Enhanced-For Loop
```java
for (String item : items) {
    // Process each item
    validate(item);
    System.out.println(item);
}
```

### Transformed to Iterator-While
```java
Iterator<String> iterator = items.iterator();
while (iterator.hasNext()) {
    String item = iterator.next();
    // Process each item
    validate(item);
    System.out.println(item);
}
```

### Back to Enhanced-For (Comments Preserved!)
```java
for (String item : items) {
    // Process each item
    validate(item);
    System.out.println(item);
}
```

---

## Comment Types Supported

### Leading Comments (Before Statement)
```java
for (String s : list) {
    // This is preserved
    System.out.println(s);
}
```

**Transformed:**
```java
list.stream()
    .forEachOrdered(s -> {
        // This is preserved
        System.out.println(s);
    });
```

### Trailing/Inline Comments (After Statement) NEW!
```java
for (String s : list) {
    System.out.println(s); // Print the item
}
```

**Transformed:**
```java
list.stream()
    .forEachOrdered(s -> {
        System.out.println(s); // Print the item
    });
```

### Filter with Trailing Comment
```java
for (String s : list) {
    if (s.isEmpty()) continue; // Skip empty strings
    System.out.println(s);
}
```

**Transformed:**
```java
list.stream()
    .filter(s -> {
        return !(s.isEmpty()); // Skip empty strings
    })
    .forEachOrdered(s -> {
        System.out.println(s);
    });
```

### Block Comments
```java
for (String s : list) {
    /* This is also preserved */
    System.out.println(s);
}
```

**Transformed:**
```java
list.stream()
    .forEachOrdered(s -> {
        /* This is also preserved */
        System.out.println(s);
    });
```

### Javadoc Comments
```java
for (String s : list) {
    /** Even Javadoc is preserved */
    System.out.println(s);
}
```

**Transformed:**
```java
list.stream()
    .forEachOrdered(s -> {
        /** Even Javadoc is preserved */
        System.out.println(s);
    });
```

### Multiple Comments
```java
for (String s : list) {
    // First comment
    // Second comment
    /* Block comment */
    System.out.println(s);
}
```

---

## Notes

1. **Comment Position**: Comments immediately before or on the same line as a statement are associated with that statement
2. **Block Lambda**: Comments trigger block lambda syntax (`{ ... return ...; }`) instead of expression lambda
3. **Automatic**: No configuration needed - comment preservation works automatically
4. **Testing**: All examples have corresponding test cases in the test suite

---

See [COMMENT_PRESERVATION.md](COMMENT_PRESERVATION.md) for detailed technical information.
