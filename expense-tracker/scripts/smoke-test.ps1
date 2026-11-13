<#
.SYNOPSIS
  End-to-end check of P1's API (auth, categories, transactions, search, ownership).
  Runs against a running app and prints PASS or FAIL for every check.

.EXAMPLE
  .\scripts\smoke-test.ps1
  .\scripts\smoke-test.ps1 -BaseUrl http://localhost:8080 -IncludeRateLimit

.NOTES
  - Start the app first (mvn spring-boot:run with DB_PASSWORD and JWT_SECRET set).
  - It creates two throw-away users (smoke-a-<time>@example.com, smoke-b-<time>@example.com).
    Remove them later with scripts/cleanup-smoke-data.sql.
  - -IncludeRateLimit also sends 12 bad logins; the login limit (10 per minute) then blocks
    you for about a minute, so run it last or wait before the next run.
  - Login and register are limited to 10 requests per minute per IP, and one run uses about 4 of each,
    so do not start more than 2 runs within a minute.
  - If PowerShell refuses to run the file:
      powershell -ExecutionPolicy Bypass -File .\scripts\smoke-test.ps1
#>
param(
    [string]$BaseUrl = "http://localhost:8080",
    [switch]$IncludeRateLimit
)

$ErrorActionPreference = "Stop"
$script:pass = 0
$script:fail = 0

# ---------- helpers ----------

function Api {
    param(
        [string]$Method,
        [string]$Path,
        $Body = $null,
        [string]$Token = ""
    )
    $params = @{ Method = $Method; Uri = "$BaseUrl$Path"; UseBasicParsing = $true }
    if ($Token -ne "") { $params.Headers = @{ Authorization = "Bearer $Token" } }
    if ($null -ne $Body) {
        $params.Body = ($Body | ConvertTo-Json -Depth 5)
        $params.ContentType = "application/json"
    }
    $status = 0
    $text = ""
    try {
        $r = Invoke-WebRequest @params
        $status = [int]$r.StatusCode
        $text = [string]$r.Content
    } catch {
        if ($null -eq $_.Exception.Response) {
            throw "Cannot reach $BaseUrl - is the app running?"
        }
        $status = [int]$_.Exception.Response.StatusCode
        $text = [string]$_.ErrorDetails.Message
        if ($text -eq "") {
            # Windows PowerShell fallback: read the error body from the response stream
            try {
                $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
                $text = $reader.ReadToEnd()
            } catch { $text = "" }
        }
    }
    $json = $null
    if ($text -ne "") {
        try { $json = $text | ConvertFrom-Json } catch { $json = $null }
    }
    return [pscustomobject]@{ Status = $status; Json = $json; Text = $text }
}

function Brief($res) {
    $t = [string]$res.Text
    if ($t.Length -gt 220) { $t = $t.Substring(0, 220) + "..." }
    return "(HTTP $($res.Status) $t)"
}

function Expect([string]$name, [bool]$condition, [string]$detail = "") {
    if ($condition) {
        Write-Host "  PASS  $name" -ForegroundColor Green
        $script:pass++
    } else {
        Write-Host "  FAIL  $name  $detail" -ForegroundColor Red
        $script:fail++
    }
}

function Section([string]$title) {
    Write-Host ""
    Write-Host "== $title" -ForegroundColor Cyan
}

function ErrorFieldCount($res, [string]$field) {
    return @($res.Json.fieldErrors | Where-Object { $_.field -eq $field }).Count
}

# ---------- test data ----------

$stamp = Get-Date -Format "yyyyMMddHHmmss"
$emailA = "smoke-a-$stamp@example.com"
$emailB = "smoke-b-$stamp@example.com"
$password = "Secret123!"
$today = (Get-Date).ToString("yyyy-MM-dd")
$tomorrow = (Get-Date).AddDays(1).ToString("yyyy-MM-dd")
$catName = "Smoke$stamp"

# ---------- 1. security basics ----------
Section "Security"
$r = Api GET "/api/transactions"
Expect "no token -> 401" ($r.Status -eq 401) (Brief $r)
Expect "401 body has the contract shape" ($r.Json.status -eq 401 -and $r.Json.error -eq "Unauthorized" -and $r.Json.message -eq "Authentication required" -and $r.Json.path -eq "/api/transactions") (Brief $r)
$r = Api GET "/api/transactions" -Token "not.a.real.token"
Expect "forged token -> 401" ($r.Status -eq 401) (Brief $r)

# ---------- 2. register and login ----------
Section "Register and login"
$r = Api POST "/api/auth/register" -Body @{ name = "Smoke A"; email = $emailA; password = $password }
Expect "register -> 201" ($r.Status -eq 201) (Brief $r)
Expect "register returns the user and no password" ($r.Json.email -eq $emailA -and $null -eq $r.Json.password) (Brief $r)
$r = Api POST "/api/auth/register" -Body @{ name = "Smoke A"; email = $emailA; password = $password }
Expect "same email again -> 409" ($r.Status -eq 409) (Brief $r)
$r = Api POST "/api/auth/register" -Body @{ name = ""; email = "not-an-email"; password = "1" }
Expect "bad input -> 400 with 3 fieldErrors" ($r.Status -eq 400 -and @($r.Json.fieldErrors).Count -eq 3) (Brief $r)
$r = Api POST "/api/auth/login" -Body @{ email = $emailA; password = "wrong-password" }
Expect "wrong password -> 401" ($r.Status -eq 401 -and $r.Json.message -eq "Invalid email or password") (Brief $r)
$r = Api POST "/api/auth/login" -Body @{ email = $emailA; password = $password }
Expect "login -> 200 with token, Bearer, expiresIn" ($r.Status -eq 200 -and $r.Json.token -and $r.Json.tokenType -eq "Bearer" -and $r.Json.expiresIn -gt 0) (Brief $r)
$tokenA = [string]$r.Json.token
$r = Api POST "/api/auth/login" -Body @{ email = $emailA.ToUpper(); password = $password }
Expect "email is case-insensitive" ($r.Status -eq 200) (Brief $r)

# ---------- 3. categories ----------
Section "Categories"
$r = Api GET "/api/categories" -Token $tokenA
$cats = @($r.Json)
Expect "list -> 200 with the default categories" ($r.Status -eq 200 -and $cats.Count -ge 13) (Brief $r)
$food = $cats | Where-Object { $_.name -eq "Food" -and $_.type -eq "EXPENSE" } | Select-Object -First 1
$salary = $cats | Where-Object { $_.name -eq "Salary" -and $_.type -eq "INCOME" } | Select-Object -First 1
Expect "Food and Salary defaults exist with isDefault = true" ($null -ne $food -and $null -ne $salary -and $food.isDefault -eq $true -and $salary.isDefault -eq $true)
if ($null -eq $food -or $null -eq $salary) { throw "Default categories Food/Salary not found - did Flyway V2 run?" }
$r = Api GET "/api/categories?type=EXPENSE" -Token $tokenA
Expect "type=EXPENSE returns only expense categories" ($r.Status -eq 200 -and @(@($r.Json) | Where-Object { $_.type -ne "EXPENSE" }).Count -eq 0) (Brief $r)
$r = Api GET "/api/categories?type=BANANA" -Token $tokenA
Expect "unknown type -> 400" ($r.Status -eq 400) (Brief $r)
$r = Api POST "/api/categories" -Token $tokenA -Body @{ name = $catName; type = "EXPENSE" }
Expect "create custom category -> 201, isDefault = false" ($r.Status -eq 201 -and $r.Json.isDefault -eq $false) (Brief $r)
$customId = $r.Json.id
$r = Api POST "/api/categories" -Token $tokenA -Body @{ name = $catName.ToUpper(); type = "EXPENSE" }
Expect "same name ignoring case -> 409" ($r.Status -eq 409) (Brief $r)
$r = Api POST "/api/categories" -Token $tokenA -Body @{ name = "  "; type = "EXPENSE" }
Expect "blank name -> 400 with a name error" ($r.Status -eq 400 -and (ErrorFieldCount $r "name") -eq 1) (Brief $r)
$r = Api PUT "/api/categories/$($food.id)" -Token $tokenA -Body @{ name = "Meals"; type = "EXPENSE" }
Expect "editing a default category -> 400" ($r.Status -eq 400) (Brief $r)
$r = Api DELETE "/api/categories/$($food.id)" -Token $tokenA
Expect "deleting a default category -> 400" ($r.Status -eq 400) (Brief $r)
$catName = "$catName-renamed"
$r = Api PUT "/api/categories/$customId" -Token $tokenA -Body @{ name = $catName; type = "EXPENSE" }
Expect "rename own category -> 200" ($r.Status -eq 200 -and $r.Json.name -eq $catName) (Brief $r)
$r = Api PUT "/api/categories/99999999" -Token $tokenA -Body @{ name = "X"; type = "EXPENSE" }
Expect "unknown category id -> 404" ($r.Status -eq 404) (Brief $r)

# ---------- 4. transactions: create and validation ----------
Section "Transactions: create and validate"
$r = Api POST "/api/transactions" -Token $tokenA -Body @{ categoryId = $food.id; type = "EXPENSE"; amount = 250.5; description = "Lunch with team"; date = $today }
Expect "create expense -> 201" ($r.Status -eq 201) (Brief $r)
Expect "response has category name and amount 250.50" ($r.Json.categoryName -eq "Food" -and [decimal]$r.Json.amount -eq [decimal]250.5) (Brief $r)
$lunchId = $r.Json.id
$r = Api POST "/api/transactions" -Token $tokenA -Body @{ type = "EXPENSE"; amount = 40; description = "COFFEE 50% off"; date = $today }
Expect "create without a category -> 201, categoryId null" ($r.Status -eq 201 -and $null -eq $r.Json.categoryId) (Brief $r)
$coffeeId = $r.Json.id
$r = Api POST "/api/transactions" -Token $tokenA -Body @{ categoryId = $customId; type = "EXPENSE"; amount = 12.345; description = "Custom category spend"; date = $today }
Expect "create with own category -> 201, amount rounded to 12.35" ($r.Status -eq 201 -and [decimal]$r.Json.amount -eq [decimal]12.35) (Brief $r)
$customTxId = $r.Json.id
$r = Api POST "/api/transactions" -Token $tokenA -Body @{ categoryId = $salary.id; type = "EXPENSE"; amount = 10; description = "x"; date = $today }
Expect "income category with type EXPENSE -> 400" ($r.Status -eq 400 -and [string]$r.Json.message -like "*does not match*") (Brief $r)
$r = Api POST "/api/transactions" -Token $tokenA -Body @{ categoryId = 99999999; type = "EXPENSE"; amount = 10; description = "x"; date = $today }
Expect "unknown category -> 404" ($r.Status -eq 404 -and $r.Json.message -eq "Category not found") (Brief $r)
$r = Api POST "/api/transactions" -Token $tokenA -Body @{ categoryId = $food.id; type = "EXPENSE"; amount = 0; description = "x"; date = $today }
Expect "amount 0 -> 400 with an amount error" ($r.Status -eq 400 -and (ErrorFieldCount $r "amount") -eq 1) (Brief $r)
$r = Api POST "/api/transactions" -Token $tokenA -Body @{ categoryId = $food.id; type = "EXPENSE"; amount = 10; description = "x" }
Expect "missing date -> 400 with a date error" ($r.Status -eq 400 -and (ErrorFieldCount $r "date") -eq 1) (Brief $r)
$r = Api POST "/api/transactions" -Token $tokenA -Body @{ categoryId = $food.id; type = "EXPENSE"; amount = 10; description = "x"; date = "1999-01-01" }
Expect "date before 2000 -> 400 (needs Step 6)" ($r.Status -eq 400) (Brief $r)
$r = Api POST "/api/transactions" -Token $tokenA -Body @{ categoryId = $food.id; type = "BANANA"; amount = 10; description = "x"; date = $today }
Expect "unknown type in the body -> 400" ($r.Status -eq 400) (Brief $r)

# ---------- 5. transactions: list, filters, paging, search ----------
Section "Transactions: list, filters, paging, search"
$r = Api GET "/api/transactions" -Token $tokenA
$names = @($r.Json.PSObject.Properties.Name)
$shape = @("content", "number", "size", "totalElements", "totalPages", "first", "last")
$missing = @($shape | Where-Object { $names -notcontains $_ })
Expect "page has exactly the contract fields" ($r.Status -eq 200 -and $missing.Count -eq 0) "missing: $($missing -join ', ')"
Expect "3 transactions, 10 per page by default" ($r.Json.totalElements -eq 3 -and $r.Json.size -eq 10) (Brief $r)
$r = Api GET "/api/transactions?type=INCOME" -Token $tokenA
Expect "type=INCOME -> 0" ($r.Json.totalElements -eq 0) (Brief $r)
$r = Api GET "/api/transactions?type=EXPENSE" -Token $tokenA
Expect "type=EXPENSE -> 3" ($r.Json.totalElements -eq 3) (Brief $r)
$r = Api GET "/api/transactions?categoryId=$($food.id)" -Token $tokenA
Expect "categoryId=Food -> 1" ($r.Json.totalElements -eq 1) (Brief $r)
$r = Api GET "/api/transactions?from=$today&to=$today" -Token $tokenA
Expect "from=to=today -> 3" ($r.Json.totalElements -eq 3) (Brief $r)
$r = Api GET "/api/transactions?from=$tomorrow" -Token $tokenA
Expect "from=tomorrow -> 0" ($r.Json.totalElements -eq 0) (Brief $r)
$r = Api GET "/api/transactions?from=2026-12-01&to=2026-11-01" -Token $tokenA
Expect "from after to -> 400" ($r.Status -eq 400) (Brief $r)
$r = Api GET "/api/transactions?from=not-a-date" -Token $tokenA
Expect "bad date -> 400" ($r.Status -eq 400) (Brief $r)
$r = Api GET "/api/transactions?sort=password,asc" -Token $tokenA
Expect "sort by a forbidden field -> 400" ($r.Status -eq 400 -and [string]$r.Json.message -like "*Cannot sort*") (Brief $r)
$r = Api GET "/api/transactions?sort=amount,asc" -Token $tokenA
Expect "sort=amount,asc puts the smallest first" ($r.Status -eq 200 -and [decimal]$r.Json.content[0].amount -eq [decimal]12.35) (Brief $r)
$r = Api GET "/api/transactions?size=2&page=0" -Token $tokenA
Expect "size=2 -> 2 items, 2 pages, first page" (@($r.Json.content).Count -eq 2 -and $r.Json.totalPages -eq 2 -and $r.Json.first -eq $true) (Brief $r)
$r = Api GET "/api/transactions?size=500" -Token $tokenA
Expect "size=500 is capped at 100" ($r.Json.size -eq 100) (Brief $r)
$r = Api GET "/api/transactions?q=lunch" -Token $tokenA
Expect "q=lunch finds 'Lunch with team' (ignores capitals)" ($r.Json.totalElements -eq 1 -and $r.Json.content[0].id -eq $lunchId) (Brief $r)
$r = Api GET "/api/transactions?q=coffee" -Token $tokenA
Expect "q=coffee finds 'COFFEE 50% off'" ($r.Json.totalElements -eq 1 -and $r.Json.content[0].id -eq $coffeeId) (Brief $r)
$pct = [uri]::EscapeDataString("50%")
$r = Api GET "/api/transactions?q=$pct" -Token $tokenA
Expect "q=50% matches the % literally (1 result, not all)" ($r.Json.totalElements -eq 1) (Brief $r)
$r = Api GET "/api/transactions?q=zzz" -Token $tokenA
Expect "q=zzz -> 0" ($r.Json.totalElements -eq 0) (Brief $r)
$r = Api GET "/api/transactions?q=lunch&type=INCOME" -Token $tokenA
Expect "q combines with other filters" ($r.Json.totalElements -eq 0) (Brief $r)
$r = Api GET "/api/transactions?q=" -Token $tokenA
Expect "blank q is ignored" ($r.Status -eq 200 -and $r.Json.totalElements -eq 3) (Brief $r)
$r = Api GET ("/api/transactions?q=" + ("x" * 101)) -Token $tokenA
Expect "q longer than 100 characters -> 400" ($r.Status -eq 400) (Brief $r)

# ---------- 6. ownership: a second user must see nothing ----------
Section "Ownership (second user)"
$r = Api POST "/api/auth/register" -Body @{ name = "Smoke B"; email = $emailB; password = $password }
Expect "register user B -> 201" ($r.Status -eq 201) (Brief $r)
$r = Api POST "/api/auth/login" -Body @{ email = $emailB; password = $password }
$tokenB = [string]$r.Json.token
Expect "login user B" ($r.Status -eq 200 -and $tokenB -ne "") (Brief $r)
$r = Api GET "/api/transactions" -Token $tokenB
Expect "B's list is empty" ($r.Status -eq 200 -and $r.Json.totalElements -eq 0) (Brief $r)
$r = Api GET "/api/transactions?q=lunch" -Token $tokenB
Expect "B cannot find A's transactions by search" ($r.Json.totalElements -eq 0) (Brief $r)
$r = Api GET "/api/transactions/$lunchId" -Token $tokenB
Expect "B GET A's transaction -> 404 (not 403)" ($r.Status -eq 404) (Brief $r)
$r = Api PUT "/api/transactions/$lunchId" -Token $tokenB -Body @{ type = "EXPENSE"; amount = 1; description = "hacked"; date = $today }
Expect "B PUT A's transaction -> 404" ($r.Status -eq 404) (Brief $r)
$r = Api DELETE "/api/transactions/$lunchId" -Token $tokenB
Expect "B DELETE A's transaction -> 404" ($r.Status -eq 404) (Brief $r)
$r = Api GET "/api/categories" -Token $tokenB
Expect "B does not see A's custom category" (@(@($r.Json) | Where-Object { $_.id -eq $customId }).Count -eq 0) (Brief $r)
$r = Api PUT "/api/categories/$customId" -Token $tokenB -Body @{ name = "Hacked"; type = "EXPENSE" }
Expect "B PUT A's custom category -> 404" ($r.Status -eq 404) (Brief $r)
$r = Api DELETE "/api/categories/$customId" -Token $tokenB
Expect "B DELETE A's custom category -> 404" ($r.Status -eq 404) (Brief $r)
$r = Api POST "/api/transactions" -Token $tokenB -Body @{ categoryId = $customId; type = "EXPENSE"; amount = 5; description = "x"; date = $today }
Expect "B cannot use A's custom category -> 404" ($r.Status -eq 404) (Brief $r)
$r = Api GET "/api/transactions/$lunchId" -Token $tokenA
Expect "A's transaction is untouched" ($r.Status -eq 200 -and $r.Json.description -eq "Lunch with team") (Brief $r)

# ---------- 7. update, delete, category in use ----------
Section "Update, delete, category in use"
$r = Api GET "/api/transactions/$lunchId" -Token $tokenA
Expect "GET one -> 200" ($r.Status -eq 200 -and $r.Json.id -eq $lunchId) (Brief $r)
$r = Api PUT "/api/transactions/$lunchId" -Token $tokenA -Body @{ categoryId = $food.id; type = "EXPENSE"; amount = 300; description = "Lunch and coffee"; date = $today }
Expect "PUT -> 200 with new amount and description" ($r.Status -eq 200 -and [decimal]$r.Json.amount -eq 300 -and $r.Json.description -eq "Lunch and coffee") (Brief $r)
$r = Api DELETE "/api/transactions/$lunchId" -Token $tokenA
Expect "DELETE -> 204" ($r.Status -eq 204) (Brief $r)
$r = Api GET "/api/transactions/$lunchId" -Token $tokenA
Expect "GET after delete -> 404" ($r.Status -eq 404 -and $r.Json.message -eq "Transaction not found") (Brief $r)
$r = Api GET "/api/transactions/99999999" -Token $tokenA
Expect "unknown transaction id -> 404" ($r.Status -eq 404) (Brief $r)
$r = Api PUT "/api/categories/$customId" -Token $tokenA -Body @{ name = $catName; type = "INCOME" }
Expect "changing the type of a used category -> 409" ($r.Status -eq 409) (Brief $r)
$r = Api DELETE "/api/categories/$customId" -Token $tokenA
Expect "deleting a category that has transactions -> 409" ($r.Status -eq 409) (Brief $r)
$r = Api DELETE "/api/transactions/$customTxId" -Token $tokenA
Expect "delete the transaction that used it -> 204" ($r.Status -eq 204) (Brief $r)
$r = Api DELETE "/api/categories/$customId" -Token $tokenA
Expect "now the category can be deleted -> 204" ($r.Status -eq 204) (Brief $r)
$r = Api DELETE "/api/transactions/$coffeeId" -Token $tokenA
Expect "clean up the last transaction -> 204" ($r.Status -eq 204) (Brief $r)

# ---------- 8. other HTTP errors stay JSON ----------
Section "Other errors stay JSON"
$r = Api GET "/api/does-not-exist" -Token $tokenA
Expect "unknown URL -> 404 JSON" ($r.Status -eq 404 -and $r.Json.status -eq 404) (Brief $r)
$r = Api PATCH "/api/transactions/1" -Token $tokenA
Expect "wrong HTTP method -> 405 JSON" ($r.Status -eq 405 -and $r.Json.status -eq 405) (Brief $r)

# ---------- 9. optional: rate limit ----------
if ($IncludeRateLimit) {
    Section "Rate limit (login: 10 per minute per IP)"
    $codes = @(1..12 | ForEach-Object { (Api POST "/api/auth/login" -Body @{ email = $emailA; password = "wrong-password" }).Status })
    Expect "bad logins return 401 first, then 429" ($codes -contains 429 -and $codes[0] -eq 401) "codes: $($codes -join ',')"
    $r = Api POST "/api/auth/login" -Body @{ email = $emailA; password = "wrong-password" }
    Expect "429 body has the contract shape" ($r.Status -eq 429 -and $r.Json.status -eq 429 -and [string]$r.Json.message -like "Too many attempts*") (Brief $r)
    Write-Host "  (wait about a minute before logging in again)" -ForegroundColor Yellow
}

# ---------- summary ----------
Write-Host ""
if ($script:fail -eq 0) {
    Write-Host "ALL $($script:pass) CHECKS PASSED" -ForegroundColor Green
} else {
    Write-Host "$($script:fail) FAILED, $($script:pass) passed" -ForegroundColor Red
    exit 1
}
