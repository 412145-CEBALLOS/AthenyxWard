# Athenyx Ward — SPEC

## Project Name

Athenyx Ward

---

# Project Overview

Athenyx Ward is a web-based SaaS platform focused on secure and intelligent communication management. The main goal of the platform is to analyze personal email communications using local artificial intelligence, heuristic analysis, and cybersecurity techniques in order to detect, prevent, and warn users about potential digital threats.

The system combines cybersecurity, productivity, automation, and accessibility features into a single centralized platform.

The project is intended primarily for individual users and older adults who need a preventive and accessible tool to protect themselves from increasingly common digital threats delivered through email communications.

---

# Main Problem to Solve

Digital threats targeting common users are increasing constantly, especially through email communication. Many users cannot easily identify malicious emails and often become victims of:

* phishing attacks,
* spoofing,
* malware,
* dangerous links,
* account theft,
* fraud,
* social engineering,
* AI-generated scams,
* fake invoices,
* impersonation attempts.

Most existing solutions are either:

* too technical,
* enterprise-focused,
* expensive,
* difficult for older adults,
* or not explanatory enough.

Athenyx Ward aims to provide:

* prevention,
* explainability,
* accessibility,
* and centralized communication security.

---

# Core Concept

The application analyzes Gmail emails using:

* local AI models,
* heuristic rules,
* metadata analysis,
* threat scoring systems,
* and explainable security analysis.

The platform will:

* detect suspicious patterns,
* evaluate risk levels,
* explain why an email is dangerous,
* and visually warn users before they interact with malicious content.

---

# Main Features

## Email Security & Threat Detection

### Gmail Integration

* Connect Gmail accounts using Google OAuth2.
* Read and analyze recent emails.

### Automatic Analysis Rules

#### Premium Users

Premium users will have:

* automatic analysis of the latest 20 emails during the first login,
* automatic analysis every time a new email is opened,
* unlimited email analyses.

#### Trial Users

Trial users will NOT receive the initial automatic analysis of the latest 20 emails.

For trial users:

* emails are analyzed only when manually opened by the user,
* the account is limited to 20 total analyses during the trial period.

---

### Threat Detection

Detect:

* phishing,
* spoofing,
* malware,
* social engineering,
* dangerous links,
* fraud attempts,
* account theft attempts,
* suspicious AI-generated emails,
* mismatched sender identity (From vs Return-Path vs Reply-To),
* mass mailing services impersonation (Mailchimp, SendGrid simulating personal email),
* suspicious timestamps (future dates, inconsistent timezones).

### Heuristic Analysis

Use 15-20 custom heuristic rules for:

* suspicious sender domains (typosquatting, lookalikes of banks/social networks),
* urgent language detection ("urgente", "inmediato", "suspendido", "verificar ahora"),
* fake login pages (URLs mimicking official login forms),
* malicious URLs (direct IPs, short domains, typosquatting),
* sender impersonation (display name mismatch with email address),
* suspicious metadata (anomalous headers, impossible dates, weird timezones),
* scam-like language patterns (prizes, inheritances, lotteries),
* suspicious attachments (.exe, .bat, .scr, .zip with executables),
* regex patterns (credit card numbers, ID documents requested in body).

### Metadata Analysis

* Extract and analyze: From, Return-Path, Reply-To, Received headers, Date.
* Detect mismatch between From and Return-Path.
* Detect when Reply-To differs from sender (possible impersonation).
* Detect anomalous timestamps (future date, inconsistent timezone).
* Extract sending domain and validate against known lists.
* Validate SPF/DKIM/DMARC records via DNS lookup.
* Detect emails from mass mailing services (Mailchimp, SendGrid) pretending to be personal.
* Compute sender trust score (0-100) with clear thresholds.

### AI-Based Analysis

Use local LLMs to:

* explain threats,
* summarize risk,
* generate natural-language warnings,
* classify suspicious behavior,
* provide human-readable analysis.

---

# Risk Evaluation System

Each analyzed email should provide:

## Threat Percentage

* Score range: 0 (safe) to 100 (dangerous).
* Calculated by weighted aggregation of all heuristic rule scores.
* Displayed as "X% riesgo" in the UI.
* If analysis is in progress, show "Analizando..." state.

## Security Traffic-Light System

* **Green** → risk < 40% (email is safe, no indicator in list).
* **Yellow** → risk 40-70% (suspicious, yellow indicator in list).
* **Red** → risk > 70% (dangerous, prominent red indicator in list).
* Visual component: percentage donut chart that changes color based on threat level.

## Explainable Results

The user must understand:

* why the email was flagged,
* what elements are suspicious,
* recommended actions.

Example:

> "This email contains a suspicious login link and urgent language commonly used in phishing attacks."

---

# User Actions

Users can:

* view analyzed emails,
* receive warnings,
* review analysis history,
* hide dangerous emails,
* delete suspicious emails.

**Premium and Admin users (not Trial):**

* mark emails as important,
* create and manage reminders from emails,
* receive reminder notifications.

---

# Productivity Features

## Important Email Marking

**Available for Premium and Admin users only (not Trial).**

When a user marks an email as important:

* the email appears in the `/important` page,
* the system shows a visual indicator (flag icon) in the email list and viewer,
* the sidebar shows a badge with the count of important emails.

No additional automation is performed.

---

## Smart Reminders

**Available for Premium and Admin users only (not Trial).**

Users can generate reminders from emails. Each reminder stores:

* reminder date and time,
* reminder message,
* done status (pending/completed).

Reminders are stored in the database and:

* displayed on a dedicated `/reminders` page (sorted by upcoming date),
* shown as a notification icon on the email list and viewer when configured,
* highlighted visually when due within the next 24 hours.

**Reminder notifications:**
* Frontend polls `/api/notifications/upcoming` every 2 minutes.
* Upcoming reminders (within 24h) are shown in the notification panel.
* When a reminder's time arrives or passes, a toast notification is shown.
* Users can mark the reminder as done directly from the notification or toast.
* The frontend tracks shown notification IDs in memory to avoid duplicates until page reload.

When entering an email that already has a reminder:

* an icon or visual mark indicates the email has a reminder configured,
* the user has the option to view, edit, or remove the reminder.

---

## Notifications

* Polling every 2 minutes to `/api/notifications/upcoming`.
* Upcoming reminders (within 24 hours) are highlighted in the notification panel.
* Toast notification is shown when a reminder's configured time arrives or has passed.
* Users can mark the reminder as done directly from the notification or toast.
* Notification IDs are tracked in frontend memory to prevent duplicate toasts (cleared on page reload).

---

# Homepage vs Dashboards

The homepage is NOT the same as the dashboards.

## Homepage

The homepage is the main email management interface where users:

* view emails,
* open emails,
* receive warnings,
* see analysis results,
* interact with reminders and important emails.

The homepage should be the first screen shown after login.

---

## User Dashboard

The user dashboard is a separate page dedicated only to:

* statistics,
* graphs,
* historical analysis information.

The dashboard should include:

* analyzed emails statistics,
* average risk percentages,
* traffic-light statistics,
* recent analysis activity,
* threat evolution graphs.

All information should be filterable by time periods such as:

* one week,
* one month,
* one year.

The dashboard should NOT automatically appear when entering the application.

---

## Admin Dashboard

The admin dashboard is also a separate page dedicated only to:

* platform metrics,
* global statistics,
* subscriptions,
* user management data.

The admin dashboard should include:

* total users,
* active subscriptions,
* canceled subscriptions,
* total analyzed emails,
* average analyses,
* global threat metrics,
* premium users vs trial users.

All information should be filterable by time periods such as:

* one week,
* one month,
* one year.

The admin dashboard should NOT automatically appear when entering the application.

---

# Accessibility Features

The platform includes two UI modes that the user can toggle in settings.

## Accessibility Mode (enabled by default)

Designed for older adults and non-technical users:

* larger text,
* high contrast colors,
* simplified interface,
* larger buttons,
* simplified alerts,
* easy-to-understand language.

## Standard Mode (accessibility disabled)

When the user disables accessibility mode, the interface switches to a more information-dense and professional view:

* smaller text and tighter spacing,
* more data visible on screen at once,
* advanced layout with additional details (metadata, raw analysis data, extended statistics),
* professional visual style oriented toward more technical or experienced users.

The default mode on first login is **Accessibility Mode**. The user can switch between modes at any time from settings.

---

# Language Support

The application will launch in **Spanish only**.

Additional languages (such as English or Portuguese) may be added in future versions, as the architecture should be built with i18n support in mind to make that extension straightforward.

The AI responses and all interface elements must be in Spanish.

---

# Authentication & Roles

## Authentication

* Google OAuth2 login
* JWT session management
* Secure authentication flow
* Secure cookies for session handling
* No password storage

---

## User Roles

### Admin

* platform management,
* metrics access,
* user management,
* all features enabled (same as Premium).

### Premium

* unlimited analyses,
* all productivity features enabled (important emails, reminders, notifications).

### Trial

* 1-month trial,
* limited to 20 email analyses,
* productivity features disabled (marking important, reminders, notifications).
* Trial users see a "Upgrade your plan" message when attempting to use restricted features.

---

# SaaS Business Model

## Subscription System

* Monthly subscription
* $5 USD/month

---

## Payment Gateway

* Mercado Pago
* PayPal

---

# AI Architecture

## AI Type

Local AI only.
No cloud AI APIs for AI/LLM processing.

External cloud APIs (Gmail API, Google OAuth2) are used only for email access and authentication — they are not part of the AI/LLM layer.

---

# AI Stack

## Framework

Spring AI

## Local Model Runner

Ollama

## LLM

Llama 3

---

# AI Responsibilities

The AI should:

* analyze email content,
* explain threats,
* classify suspicious behavior,
* assist with risk scoring,
* generate natural-language responses.

The AI is NOT intended to:

* act autonomously,
* execute actions independently,
* behave like a multi-agent system.

This is a:

> hybrid heuristic + local AI analysis system

NOT:

> an autonomous agent platform.

---

# Backend Architecture

## Backend Stack

* Java
* Spring Boot
* Spring Security
* Spring AI
* Hibernate/JPA
* JWT
* MySQL

## Architecture Style

Client-server architecture.

---

# Frontend Stack

## Frontend

* Angular

## UI Goals

* responsive,
* modern,
* simple,
* accessible,
* dashboard-oriented.

## Key UI Components

| Component | Description |
|-----------|-------------|
| `ThreatDonutComponent` | Percentage donut chart with traffic-light color (green/yellow/red) |
| `ReminderIndicatorComponent` | Chip showing 🔔 icon + reminder date, integrated in email-list and email-viewer |

---

# Database

## Database

MySQL

## Main Entities

| Entity | Description |
|--------|-------------|
| `User` | User account with Google ID, role, accessibility mode, analysis count |
| `Email` | Gmail message with sender, subject, content, URLs, **isImportant** flag |
| `EmailAnalysis` | Analysis result (1:N with Email — re-analysis creates new record) |
| `Reminder` | Reminder linked to an Email (1:1 — one reminder per email) |
| `RefreshToken` | JWT refresh token with family-based revocation |
| `Role` | Enum: ADMIN / PREMIUM / TRIAL |
| `Subscription` | (Sprint 4) |
| `Payment` | (Sprint 4) |

**EmailAnalysis** centralizes:
* threat analysis results,
* historical analysis records,
* risk percentages,
* AI explanations,
* timestamps,
* risk classifications,
* heuristic findings (JSON),
* suspicious URLs (JSON),
* sender trust data (JSON).

---

# APIs

## External APIs

### Gmail API

For reading emails.

### Google OAuth2

For secure authentication.

---

## Internal APIs

### Analysis

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/emails/{id}/analyze` | Trigger heuristic analysis (uses 24h cache if recent) |
| GET | `/api/emails/{id}/analysis` | Get latest analysis result for an email |
| GET | `/api/analysis/history` | Get paginated analysis history (filter by date range) |

### Important Emails

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/emails/important` | List user's important emails (newest first) |
| GET | `/api/emails/important/count` | Returns `{ count: number }` — used for the sidebar badge |
| POST | `/api/emails/{id}/important` | Toggle important flag (403 if Trial) |

### Reminders

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/reminders` | List all user's reminders |
| POST | `/api/reminders` | Create reminder (409 if already exists for email, 403 if Trial) |
| PATCH | `/api/reminders/{id}` | Update reminder (date, message, done) |
| DELETE | `/api/reminders/{id}` | Delete reminder |

### Notifications

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/notifications/upcoming` | Get reminders due within 24 hours (403 if Trial) |

---

# Security

## Security Features

* JWT authentication,
* OAuth2 login,
* role-based authorization,
* protected endpoints,
* secure token storage,
* encrypted sensitive data,
* secure cookies,
* session expiration,
* backend authorization validation.

---

# Testing

## Testing Stack

* JUnit
* Mockito

---

# Project Scope Constraints

## Current Scope

* Gmail only
* Individual users only
* Web platform only
* Responsive design

---

# Sprint Planning

## Sprint 1

Authentication & Gmail integration:

* OAuth2 login,
* JWT,
* Gmail API integration,
* user roles,
* base homepage structure (navigation, email list, email viewer — analysis features not yet active).

---

## Sprint 2

Threat detection engine + Productivity features:

**Threat Detection:**
* heuristic rules (15-20 rules covering domains, language, URLs, metadata, patterns),
* metadata analysis (From/Return-Path/Reply-To validation, SPF/DKIM/DMARC, sender trust score),
* threat scoring (0-100% with weighted algorithm),
* traffic-light system (Green <40%, Yellow 40-70%, Red >70%),
* async analysis (<1 second, non-blocking),
* 24h analysis cache (no re-analysis if recent result exists),
* analysis history with pagination and date filters,
* homepage updated with real analysis results and warnings.

**Productivity (Premium/Admin only, NOT Trial):**
* mark emails as important (with sidebar badge count),
* smart reminders from emails (CRUD, date/time/message, done status),
* visual indicator on emails with active reminders,
* reminder notifications via polling (every 2 minutes),
* toast notifications when reminder time arrives/passes,
* mark done directly from notification.

---

## Sprint 3

Local AI integration:

* Spring AI + Ollama + Llama 3 integration,
* explainable AI analysis (HYBRID mode — supplements heuristic rules),
* AI-generated threat explanations and natural-language warnings,
* email actions (hide, delete).

---

## Sprint 4

Dashboards & SaaS:

* admin dashboard,
* user dashboard,
* Mercado Pago,
* PayPal,
* subscriptions,
* trial system,
* accessibility improvements.

---

# Development Philosophy

The project should prioritize:

* security,
* explainability,
* simplicity,
* accessibility,
* maintainability.

The platform should feel:

* professional,
* modern,
* trustworthy,
* understandable for non-technical users.

---

# Important Technical Notes

## Do NOT Overengineer

Avoid:

* microservices,
* Kubernetes,
* Kafka,
* Redis,
* event-driven architecture,
* complex AI agent systems.

This is a university final project with limited development time.
