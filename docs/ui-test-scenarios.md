# ClaimSphere: UI Test Scenarios (click-through tour)

These scenarios walk you through everything the app can do, using only the browser. They tell one story: a customer reports a car accident, an officer investigates it, asks for evidence, approves and pays it, and a manager oversees the team. After that come the alternative paths (reject, withdraw, reassign) and the security checks.

Each scenario shows:
- **Why it matters:** what the feature is for, in business terms.
- **Log in as:** which demo user to use.
- **Steps:** exactly what to click, using the labels you will see on screen.
- **You should see:** the expected result. If you see something else, it's a bug.
- **Behind the scenes:** what the system did, so you can explain it.

For the technical flows and SQL checks, see [presentation-guide.md](presentation-guide.md).

**How these were checked:** on 15 Nov 2026, TC-01 to TC-07, TC-09 to TC-14 and TC-21 were clicked through in the browser against the running app. Exception: the empty-location message in TC-05 was confirmed from the form code, not clicked. The other scenarios rely on server rules covered by the automated tests and the earlier API run, but haven't been clicked through screen by screen.

---

## Before you start

| What | Value |
|---|---|
| App | http://localhost:4200 |
| Password for every account | `ClaimSphere#2026` |
| Database console (optional) | http://localhost:8082 (see presentation guide, section 5.3) |

**The cast (seeded demo data):**

| Login | Name on screen | Role | Starts with |
|---|---|---|---|
| `claimant@claimsphere.local` | Casey Tan | Claimant, Singapore | Motor policy (SGD 25,000 cover), Travel policy (SGD 10,000 cover). Claim **000001** (motor, submitted, nobody assigned) and **000005** (travel, settled) |
| `claimant2@claimsphere.local` | Arjun Mehta | Claimant, India | **000002** (health, INR 185,000, under review by Jordan, high priority) and **000003** (waiting for Arjun's information) |
| `claimant3@claimsphere.local` | Mia Wilson | Claimant, Australia | **000004** (property, approved for AUD 45,000, not paid yet) |
| `officer@claimsphere.local` | Jordan Officer | Claims officer | Handling 000002 |
| `officer2@claimsphere.local` | Priya Officer | Claims officer | Handling 000003 and 000004 |
| `manager@claimsphere.local` | Morgan Manager | Manager | Sees everything |
| `admin@claimsphere.local` | – | Admin | Sees all claims |

Claim numbers on screen are written in full, e.g. `CLM-2026-000001`.

**Tips**
- To play two people at once, use a normal window for one user and an **InPrivate/Incognito window** for the other. Each window keeps its own login.
- Data lives in memory in this local run. Restarting the backend resets everything to the table above, so you can repeat the tour as many times as you like.
- If you skip ahead or repeat a scenario, your new claim's number will differ from the ones below. Use whatever number the app shows.

---

## Part 1: The claimant experience

### TC-01: Each role lands on its own home page

**Why it matters:** customers, staff and managers do completely different jobs, so each gets a tailored workspace instead of one cluttered screen.

**Steps**
1. Open http://localhost:4200. You're sent to the **Sign in** page.
2. Sign in as `claimant@claimsphere.local`.
3. Click **Sign out** (top right, the logout icon). Sign in as `officer@claimsphere.local`, then sign out again. Sign in as `manager@claimsphere.local`.

**You should see**

| User | Lands on | Left menu |
|---|---|---|
| Claimant | "Welcome, Casey Tan" | My claims, Report incident, Policies, Notifications |
| Officer | "Officer workspace" | Workspace, All claims, Policies, Notifications |
| Manager | "Claims dashboard" | Dashboard, All claims, Policies, Notifications |

The top bar shows the user's name and role, e.g. "Casey Tan · Claimant".

**Behind the scenes:** the login returns a signed token (JWT) that contains the user's role. The app reads the role to choose the home page, and the server checks the same token on every request.

---

### TC-02: Wrong password is rejected

**Steps:** sign out, then sign in as `claimant@claimsphere.local` with password `wrong`.

**You should see:** you stay on Sign in, and a message at the bottom says **"Invalid email or password"**.

**Behind the scenes:** the message deliberately doesn't say whether the email exists, so attackers can't use the login page to discover customer emails. Passwords are stored only as BCrypt hashes.

---

### TC-03: Claimant dashboard and policies

**Log in as:** Casey (`claimant@claimsphere.local`)

**Steps**
1. Look at the four summary cards on the home page.
2. Click **Policies** in the menu.

**You should see**
- Cards: **2 Claims**, **1 In progress**, **0 Need your input**, **1 Settled**.
- Below them, the table **My claims** lists only Casey's claims, 000001 and 000005, never anyone else's.
- **Policies** shows Casey's two policies: Travel (cover SGD 10,000, excess SGD 100) and Motor (cover SGD 25,000, excess SGD 500), both **Active** with their start and end dates.

**Behind the scenes:** the server filters claims by the logged-in user. The browser never receives other customers' data, so this isn't just hidden on screen.

---

### TC-04: Report an incident (create a claim)

**Why it matters:** this is how every claim starts: first notice of loss.

**Log in as:** Casey

**Steps**
1. Click **Report incident** (menu) or **Report an incident** (home page).
2. Fill in:
   - **Policy:** the Motor policy
   - **Incident date:** yesterday
   - **Where did it happen?** `Orchard Road, Singapore`
   - **Estimated loss:** `2400`
   - **What happened?** `Another car hit my rear bumper while parked.`
3. Optional: under **Supporting documents**, click **Add files** and pick a photo (PNG/JPEG) or PDF.
4. Click **Submit claim**.

**You should see**
- The new claim **CLM-2026-000006** opens with status **Submitted**.
- Currency is **SGD**, taken from the policy. The customer never picks a currency.
- The **Timeline** shows "Submitted" with Casey's name.
- Under **Actions**, Casey can **Edit details** or **Withdraw**.

**Behind the scenes:** in one database transaction the claim, its first history entry, an audit record and an event are saved. The event then goes through Kafka and creates a "Claim … has been submitted" notification.

---

### TC-05: The form protects against bad claims

**Log in as:** Casey, on **Report incident**

Try each of these, then fix it before trying the next:

| Try | You should see | Business rule |
|---|---|---|
| Leave **Where did it happen?** empty | Red message **"Location is required"**; the claim isn't submitted | Mandatory information |
| Type tomorrow's date into **Incident date** (the date picker won't offer future dates) | The server refuses: incident date cannot be in the future | Can't claim for something that hasn't happened |
| Motor policy with **Estimated loss** `30000` | Message at the bottom: **"Estimated loss exceeds the policy coverage of 25000.00 SGD"** | Can't claim more than the policy covers |

**Behind the scenes:** the screen checks the simple things, but the **server re-checks everything**, so the rules can't be bypassed by calling the API directly. The coverage and date rules above are enforced only by the server.

---

### TC-06: Correct a claim before it's reviewed

**Log in as:** Casey, open **CLM-2026-000006**

**Steps:** **Actions → Edit details**, change **Estimated loss** to `2500` and the **Description**, then click **Confirm**.

**You should see:** the new amount and description on the claim.

**Why it matters:** customers can fix mistakes while the claim is **Submitted**, or when an officer asks for information. Once an officer is reviewing, details are locked so the officer isn't assessing a moving target. Every edit is audited.

---

## Part 2: The claims officer experience

### TC-07: Officer workspace and picking up work

**Log in as:** Jordan (`officer@claimsphere.local`)

**Steps**
1. Look at the six counters at the top.
2. Open the **Unassigned queue** tab.
3. On **CLM-2026-000006**, click **Assign to me**.

**You should see**
- Counters: **My open claims**, **Under review**, **Awaiting claimant**, **Approved, to settle**, **Unassigned queue**, **Older than 30 days**. Each one is a to-do list.
- The **My claims** tab shows 000002 (Arjun, Health, India, INR 185,000, **High** priority).
- After **Assign to me**, claim 000006 moves into **My claims** with status **Under review**.

**Behind the scenes:** if two officers press **Assign to me** at the same moment, only one wins; the other gets "already assigned". Casey is notified that the claim is being reviewed, and Jordan is notified that it was assigned to him.

---

### TC-08: All claims, with filters

**Log in as:** Jordan, then click **All claims**

**Steps:** try the filters **Status**, **Claim type**, **Market** and **Priority**, e.g. Market = India.

**You should see:** every claim in the company, with columns for claimant, type, market, amount, priority, status, assigned officer and age in days. Officers need the full picture; claimants only ever see their own.

---

### TC-09: Record an internal assessment

**Log in as:** Jordan, open **CLM-2026-000006**

**Steps:** **Actions → Record assessment**, enter **Assessment notes (internal)** `Photos consistent with rear impact; garage quote reasonable.` and **Recommended amount** `2300`, then **Confirm**.

**You should see:** an **Assessment notes** card with the note, the amount, Jordan's name and the time. The status doesn't change.

**Why it matters:** it's the officer's working file. It's **internal**: when Casey opens the same claim, there's no Assessment notes card. The recommended amount can't exceed the customer's estimate.

---

### TC-10: Ask the customer for more information

**Log in as:** Jordan, on **CLM-2026-000006**

**Steps:** **Actions → Request information**, enter `Please upload the police report or the other driver's details.` in **What do you need from the claimant?**, then **Confirm**.

**You should see**
- Status changes to **Info requested**.
- An **Information requests** card shows the question as open.
- Back in **Workspace**, the **Awaiting claimant** counter goes up.

**Behind the scenes:** the clock is now on the customer, and the claim stays in the officer's name.

---

### TC-11: The customer responds

**Log in as:** Casey (or use an InPrivate window)

**Steps**
1. On the home page, the **Need your input** card now shows **1**.
2. Open **CLM-2026-000006**. The officer's question is in **Information requests**.
3. In **Documents**, choose **Document type** = Police report, upload a PDF or image, and click **Upload**.
4. **Actions → Provide information**, type `Police report uploaded, reference SPF-2026-118.` in **Your response**, then **Confirm**.

**You should see:** status back to **Under review**; the request shows Casey's answer under the question; the document appears in the list (name, type, size, who uploaded it) and can be downloaded by clicking its name. Jordan receives a notification.

**Extra check, file safety:** the file chooser only offers PDF, PNG and JPEG. Rename a text file to `something.pdf` and upload it: the server reads the real contents and refuses with **"Only PDF, PNG and JPEG documents are accepted"**. A disguised file can't get in.

---

### TC-12: Approve, with the money rules enforced

**Log in as:** Jordan, on **CLM-2026-000006**

**Steps**
1. **Actions → Approve**, type `3000` in **Approved amount (SGD)** (more than Casey's 2,500 estimate).
2. Change it to `2300`, add **Comment** `Within policy terms`, then click **Confirm**.

**You should see**
1. The hint under the field reads **"Up to SGD 2,500.00"** and **Confirm** stays disabled, so you can't approve more than was claimed. (The server enforces the same rule if anyone bypasses the screen.)
2. Status **Approved**, and a **Decision: Approved** card showing Jordan Officer, the time, **Approved amount: SGD 2,300.00** and **Comment: Within policy terms**.

**Why it matters:** an officer can never approve more than was claimed. Money is held as exact decimals, so no rounding errors.

---

### TC-13: Settle (pay) the claim

**Log in as:** Jordan, on **CLM-2026-000006**

**Steps**
1. **Actions → Settle**, type `2500` in **Settled amount** (more than approved).
2. Change it to `2300`, leave **Settlement date** as today, set **Payment reference (optional)** to `PAY-000981`, then click **Confirm**.

**You should see**
1. The hint reads **"Up to the approved SGD 2,300.00"** and **Confirm** stays disabled. The date picker also won't accept a future settlement date.
2. Status **Settled**. The **Actions** card disappears completely, because a settled claim is closed for good.
3. The **Timeline** tells the whole story: Submitted → Under review → Info requested → Under review → Approved → Settled, each step with who did it and when.

---

### TC-14: The customer sees the outcome and their notifications

**Log in as:** Casey

**Steps**
1. Open **CLM-2026-000006**: **Decision: Approved** and the settlement details are visible.
2. Click **Notifications**.
3. Click **Mark as read** (the ✓ icon) on one message, then turn on **Unread only**.
4. Click any message.

**You should see:** one message per milestone, newest first, e.g. "Claim CLM-2026-000006 has been settled for 2300.00 SGD."; the read one disappears under **Unread only**; clicking a message opens that claim.

**Behind the scenes:** notifications are created by a separate part of the system that listens to Kafka events, not by the approve button itself. If the notification part were slow or down, approving would still work, and notifications would catch up afterwards.

---

## Part 3: Other paths through the workflow

### TC-15: Reject a claim

**Log in as:** Jordan, **All claims →** open **CLM-2026-000002** (Arjun, INR 185,000, under review by Jordan)

**Steps:** **Actions → Reject**, enter **Reason (shared with the claimant)** `Treatment is excluded under section 4.2 (pre-existing condition).`, then **Confirm**.

**You should see:** status **Rejected**, a **Decision: Rejected** card with the reason, and no more actions. Then log in as Arjun (`claimant2@claimsphere.local`): he sees the same reason on his claim and a notification. A rejection always has to explain itself to the customer.

---

### TC-16: The customer withdraws a claim

**Log in as:** Casey

**Steps:** report a new small claim (as in TC-04, e.g. Travel policy, SGD 150), then **Actions → Withdraw**, optional comment `Found my luggage`, **Confirm**.

**You should see:** status **Withdrawn**, closed permanently. Withdrawal is allowed while submitted, under review or awaiting information, but **not** once approved: open Mia's or any approved claim and there's no Withdraw button.

---

### TC-17: An officer can't act on a colleague's claim

**Log in as:** Priya (`officer2@claimsphere.local`)

**Steps:** **All claims →** open **CLM-2026-000001** if still unassigned, then open a claim that's under review by **Jordan**.

**You should see:** Priya can **Assign** an unassigned claim, but on Jordan's claim she can only **Change priority**. There's no Approve, Reject or Request information. Only the assigned officer makes the decision; this is segregation of duties.

---

## Part 4: The manager experience

### TC-18: Read the manager dashboard

**Log in as:** Morgan (`manager@claimsphere.local`)

**You should see** (numbers change as you run the scenarios above):
- **Top cards:** Claims, Outstanding, Unassigned, Pending > 30 days, Avg. days to close.
- **Liability by currency:** one row each for **AUD, SGD and INR**, with estimated exposure, approved liability (not yet paid), settled, and outstanding claims. The subtitle says *"Amounts are never converted or summed across currencies"*. Adding INR to AUD would produce a meaningless number, so the app refuses to.
- **Claims by status**, **Ageing of outstanding claims** (0–7, 8–30, 31–60, 61–90, 90+ days), **Claims by market**, and **Officer workload** (open, decided and settled per officer).
- **Metric definitions** (expand at the bottom): how each number is calculated.

**Steps:** set **Market** = India and watch every section recalculate; set **Ageing threshold (days)** = 1; then click **Reset**.

---

### TC-19: Assign and reassign work

**Log in as:** Morgan

**Steps**
1. **All claims →** open an unassigned **Submitted** claim (e.g. 000001) → **Actions → Assign**, choose **Claims officer** = Priya Officer, then **Confirm**.
2. Open a claim that's **Under review** or **Info requested** → **Actions → Reassign**, choose **New claims officer**, then **Confirm**.

**You should see:** after step 1, status **Under review** with Priya as officer. After step 2 the officer changes but the **status doesn't**, because the work continues where it left off. The **Officer workload** table on the dashboard reflects both. The current officer is never offered as a reassignment choice.

---

### TC-20: Change priority and settle as a manager

**Log in as:** Morgan

**Steps**
1. Open any open claim → **Actions → Change priority** → **Priority** = Urgent → **Confirm**.
2. Open **CLM-2026-000004** (Mia, approved for AUD 45,000) → **Actions → Settle**, amount `45000`, reference `PAY-AU-0042` → **Confirm**.

**You should see:** the priority chip updates in every list. Claim 000004 becomes **Settled**: a manager can complete payment when the assigned officer is away. On the dashboard, AUD **Approved liability** drops and **Settled** rises.

---

## Part 5: Security you can see

### TC-21: Pages are protected by role

**Log in as:** Casey

**Steps:** type http://localhost:4200/manager into the address bar.

**You should see:** you're sent straight back to Casey's own home page (`/claimant`). Even if someone forced the page open, every manager API call would be refused by the server (403), because the screen guard is only a convenience.

---

### TC-22: Customers can't see each other's claims

**Steps**
1. Log in as Arjun (`claimant2@…`), open his claim, and copy the address from the browser (it looks like `/claims/5a7f25ea-…`).
2. Sign out, log in as Casey, and paste that address.

**You should see:** a "not found" message. The server answers **not found** rather than "forbidden", so nobody can even confirm that another customer's claim exists.

---

### TC-23: Signing out and closing the tab

**Steps:** click **Sign out**, then press the browser's Back button. Separately, log in, close the tab, open a new tab and go to http://localhost:4200.

**You should see:** both times, you're asked to sign in again. The login token is stored only for the current tab and expires after 60 minutes, at which point the app signs you out by itself.

---

## Part 6: Registration and administration

### TC-24: A new customer registers

**Steps:** on **Sign in**, click **New claimant? Create an account**, fill in **Full name**, **Email**, **Password** (at least 8 characters), **Market** and optionally **Phone**, then click **Create account**.

**You should see:** you're logged in straight away as a **Claimant**. **Report incident** says *"You have no active policies to claim against."*, because policies are issued by the insurer, not created by customers. Self-registration can **only** create claimants; nobody can register themselves as an officer or manager.

---

### TC-25: The admin view

**Log in as:** `admin@claimsphere.local`

**You should see:** the admin lands on **All claims** and can see every claim and assign or reassign officers, but **cannot** approve, reject or settle; those are claims decisions. Creating staff users and policies is done through the API (Swagger at http://localhost:8080/swagger-ui.html). There's no admin screen yet, which is a known limitation.

---

## Quick reference: who can do what

| Action | Claimant (own claims) | Officer | Manager | Admin |
|---|:-:|:-:|:-:|:-:|
| Report incident, edit details, provide information, withdraw | ✅ | | | |
| Upload documents (open claims) | ✅ | ✅ | ✅ | |
| Assign | | ✅ to self | ✅ to anyone | ✅ to anyone |
| Reassign | | | ✅ | ✅ |
| Record assessment, request information, approve, reject | | ✅ assigned only | | |
| Settle | | ✅ assigned only | ✅ | |
| Change priority | | ✅ | ✅ | |
| Manager dashboard | | | ✅ | |
| See all claims | | ✅ | ✅ | ✅ |

## Quick reference: claim statuses

| Status | Meaning | Who moves it on |
|---|---|---|
| Submitted | Reported, waiting for an officer | Officer or manager (assign); claimant (withdraw) |
| Under review | An officer is investigating | Officer (request info, approve, reject); claimant (withdraw) |
| Info requested | Waiting for the customer | Claimant (provide information, withdraw) |
| Approved | Amount agreed, not yet paid | Officer or manager (settle) |
| Settled | Paid. **Closed** | – |
| Rejected | Declined with a reason. **Closed** | – |
| Withdrawn | Cancelled by the customer. **Closed** | – |
