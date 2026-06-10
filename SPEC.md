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
* suspicious AI-generated emails.

### Heuristic Analysis

Use custom heuristic rules for:

* suspicious sender domains,
* urgent language detection,
* fake login pages,
* malicious URLs,
* suspicious metadata,
* impersonation attempts,
* scam-like language patterns.

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

Example:

* 12% risk
* 67% risk
* 91% risk

## Security Traffic-Light System

* Green → safe
* Yellow → suspicious/moderate risk
* Red → dangerous/high risk

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

Premium users can additionally:

* mark emails as important,
* create reminders from emails.

---

# Productivity Features

## Important Email Marking

Available only for Premium users.

When a user marks an email as important:

* the email will move to the top of the email list,
* the system will visually indicate that the email is marked as important.

No additional automation will be performed.

---

## Smart Reminders

Available only for Premium users.

Users can generate reminders from emails.

The reminder system only stores:

* the reminder date,
* the reminder message.

These reminders are stored in the database and displayed as notifications when the configured date approaches.

When entering the email associated with a reminder:

* an icon or visual mark must indicate that the email already has a reminder configured,
* the user must also have the option to remove the reminder.

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
* user management.

### Premium

* unlimited analyses,
* all features enabled.

### Trial

* 1-month trial,
* limited to 20 email analyses,
* productivity features disabled.

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

---

# Database

## Database

MySQL

## Main Entities

Potential entities:

* User
* Role
* Email
* EmailAnalysis
* Reminder
* Subscription
* Payment
* Notification

The EmailAnalysis entity should centralize:

* threat analysis results,
* historical analysis records,
* risk percentages,
* AI explanations,
* timestamps,
* risk classifications,
* heuristic analysis data.

This avoids redundancy between ThreatAnalysis and AnalysisHistory concepts.

---

# APIs

## External APIs

### Gmail API

For reading emails.

### Google OAuth2

For secure authentication.

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

Threat detection engine:

* heuristic rules,
* metadata analysis,
* threat scoring,
* traffic-light system,
* analysis history,
* homepage updated with analysis results and warnings.

---

## Sprint 3

Local AI integration:

* Spring AI,
* Ollama,
* Llama 3,
* explainable AI analysis,
* reminders,
* email actions.

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
