# Handling Special Characters in Passwords

## Quick Answer

**For Java Properties Files (Spark/CDM):**
- **No quotes needed** (single or double)
- **Use URL encoding** for special characters in passwords
- Or **escape** certain characters with backslash

---

## Special Characters in Passwords

### Characters That Need Special Handling:

| Character | URL Encoded | Notes |
|-----------|-------------|-------|
| `/` | `%2F` | Forward slash |
| `+` | `%2B` | Plus sign |
| `)` | `%29` | Closing parenthesis |
| `(` | `%28` | Opening parenthesis |
| `-` | `-` or `%2D` | Hyphen (usually OK as-is) |
| `=` | `%3D` | Equals sign |
| `&` | `%26` | Ampersand |
| `?` | `%3F` | Question mark |
| `#` | `%23` | Hash/pound |
| `%` | `%25` | Percent sign |
| `@` | `%40` | At sign |
| `:` | `%3A` | Colon |
| `;` | `%3B` | Semicolon |
| ` ` (space) | `%20` or `+` | Space |

---

## Method 1: URL Encoding (Recommended)

### For passwords with special characters, use URL encoding:

**Example Password:** `MyP@ss/word+123)`

**In Properties File:**
```properties
spark.cdm.connect.target.yugabyte.password=MyP%40ss%2Fword%2B123%29
```

**Breakdown:**
- `@` → `%40`
- `/` → `%2F`
- `+` → `%2B`
- `)` → `%29`

---

## Method 2: No Quotes (Standard)

**Java properties files don't use quotes:**

```properties
# ✅ CORRECT - No quotes
spark.cdm.connect.target.yugabyte.password=MyP@ss/word+123)

# ❌ WRONG - Quotes are treated as part of the value
spark.cdm.connect.target.yugabyte.password="MyP@ss/word+123)"
spark.cdm.connect.target.yugabyte.password='MyP@ss/word+123)'
```

**If you use quotes, they become part of the password!**

---

## Method 3: Escape with Backslash (Limited)

**Some characters can be escaped with backslash:**

```properties
# For certain characters, you can escape
spark.cdm.connect.target.yugabyte.password=MyP@ss\/word\+123\)
```

**Note:** This works for some characters but **URL encoding is more reliable**.

---

## Complete Examples

### Example 1: Password with `/`, `+`, `)`

**Password:** `Pass/word+123)`

**Properties File:**
```properties
# Option A: URL encoded (RECOMMENDED)
spark.cdm.connect.target.yugabyte.password=Pass%2Fword%2B123%29

# Option B: Try as-is first (might work)
spark.cdm.connect.target.yugabyte.password=Pass/word+123)
```

---

### Example 2: Password with `@`, `/`, `+`

**Password:** `MyP@ss/word+123`

**Properties File:**
```properties
spark.cdm.connect.target.yugabyte.password=MyP%40ss%2Fword%2B123
```

---

### Example 3: Password with `=`, `&`, `?`

**Password:** `Pass=word&test?123`

**Properties File:**
```properties
spark.cdm.connect.target.yugabyte.password=Pass%3Dword%26test%3F123
```

---

## URL Encoding Reference

### Common Characters:

```
Space   → %20 or +
!       → %21
"       → %22
#       → %23
$       → %24
%       → %25
&       → %26
'       → %27
(       → %28
)       → %29
*       → %2A
+       → %2B
,       → %2C
-       → %2D (or just -)
.       → %2E
/       → %2F
:       → %3A
;       → %3B
<       → %3C
=       → %3D
>       → %3E
?       → %3F
@       → %40
[       → %5B
\       → %5C
]       → %5D
^       → %5E
_       → %5F
`       → %60
{       → %7B
|       → %7C
}       → %7D
~       → %7E
```

---

## Quick Encoding Tool

### Using Python:

```python
import urllib.parse

password = "MyP@ss/word+123)"
encoded = urllib.parse.quote(password, safe='')
print(f"Encoded: {encoded}")
# Output: MyP%40ss%2Fword%2B123%29
```

### Using Java:

```java
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

String password = "MyP@ss/word+123)";
String encoded = URLEncoder.encode(password, StandardCharsets.UTF_8);
System.out.println("Encoded: " + encoded);
```

### Using Online Tool:

- Search for "URL encoder" online
- Paste your password
- Copy the encoded version

---

## Testing Your Password

### Step 1: Test Connection Manually

```bash
# Test with URL-encoded password
ysqlsh -h host -p 5433 -U username -d database
# Enter password when prompted (use original, not encoded)

# Or test with connection string
ysqlsh "host=your-host port=5433 user=username password='original-password' dbname=database"
```

### Step 2: Verify in Properties File

```properties
# After encoding, your properties file should look like:
spark.cdm.connect.target.yugabyte.username=YBSQLUSER_DEV_EMER2
spark.cdm.connect.target.yugabyte.password=Pass%2Fword%2B123%29
```

### Step 3: Test CDM Connection

```bash
# Run CDM and check logs
# Should see connection succeed
# If still fails, check if encoding is correct
```

---

## Common Mistakes

### ❌ Mistake 1: Using Quotes

```properties
# WRONG - Quotes become part of password
spark.cdm.connect.target.yugabyte.password="MyP@ss/word+123)"
# This tries to use password: "MyP@ss/word+123)" (with quotes)
```

### ❌ Mistake 2: Not Encoding Special Characters

```properties
# WRONG - Special characters break JDBC URL parsing
spark.cdm.connect.target.yugabyte.password=Pass/word+123)
# The / and + and ) will be interpreted as URL delimiters
```

### ❌ Mistake 3: Double Encoding

```properties
# WRONG - Encoding twice
spark.cdm.connect.target.yugabyte.password=Pass%252Fword%252B123%2529
# This encodes the % signs, making it wrong
```

### ✅ Correct: URL Encoded

```properties
# CORRECT - Single URL encoding
spark.cdm.connect.target.yugabyte.password=Pass%2Fword%2B123%29
```

---

## For Your Specific Case

**Password contains:** `/`, `+`, `)`

**Properties File:**
```properties
# Original password: MyP@ss/word+123)
# URL encoded:
spark.cdm.connect.target.yugabyte.password=MyP%40ss%2Fword%2B123%29
```

**Or if password is simpler:**
```properties
# If password is: Pass/word+123)
spark.cdm.connect.target.yugabyte.password=Pass%2Fword%2B123%29
```

---

## Summary

1. **No quotes needed** (single or double) in properties files
2. **URL encode special characters** for JDBC URLs
3. **Test manually first** to verify password works
4. **Use URL encoding tool** to convert password
5. **Check logs** if connection still fails after encoding

---

## Quick Reference

```properties
# Password: Pass/word+123)
# Encoded:  Pass%2Fword%2B123%29

spark.cdm.connect.target.yugabyte.username=YBSQLUSER_DEV_EMER2
spark.cdm.connect.target.yugabyte.password=Pass%2Fword%2B123%29
```

**Remember:** No quotes, use URL encoding for special characters!

