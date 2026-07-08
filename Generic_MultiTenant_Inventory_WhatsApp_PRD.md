# Generic Multi-Tenant Inventory & WhatsApp Ordering Platform (PRD + Architecture)

## Vision

Build a production-ready, configurable SaaS inventory and ordering
platform that works across any industry (dairy, grocery, hardware,
pharma, electronics, agriculture, etc.).

## Core Goals

-   Multi-tenant / Multi-vendor
-   WhatsApp-first ordering
-   Web UI for vendors
-   Customer order history
-   Inventory management
-   Audit trail
-   Configurable product catalog
-   Production-ready architecture
-   Java + Spring Boot + JPA + PostgreSQL + Thymeleaf

## Actors

-   Super Admin
-   Vendor (Tenant)
-   Customer

## Functional Requirements

### Vendor

-   Register organization
-   Configure branding, language, currency, timezone
-   Manage categories, products, prices, stock, SKU, units
-   Receive WhatsApp order notifications
-   Manage orders
-   Dashboard & reports
-   User/role management

### Customer

-   Browse categories
-   View products, prices, stock availability (optional)
-   Add to cart
-   Confirm order
-   Receive unique Order ID
-   View order history
-   Order status notifications

### WhatsApp Flow

1.  Language selection
2.  Browse categories
3.  Browse products
4.  Add quantity
5.  Review cart
6.  Confirm
7.  Generate Order ID
8.  Notify customer
9.  Notify vendor
10. Vendor fulfills order

## Non-functional

-   JWT + Refresh Token
-   Spring Security
-   Rate limiting
-   Validation
-   Optimistic locking
-   Redis cache
-   Audit logs
-   Docker
-   CI/CD
-   Flyway migrations
-   OpenAPI/Swagger
-   Structured logging
-   Monitoring (Micrometer + Prometheus + Grafana)

## Multi-Tenant Model

Each customer belongs to exactly one vendor. Incoming WhatsApp number
resolves tenant before business logic executes.

Vendor A - 100 customers

Vendor B - 300 customers

Tenant isolation enforced in service and persistence layers.

## Suggested Modules

-   authentication-service
-   tenant-service
-   customer-service
-   product-service
-   inventory-service
-   pricing-service
-   order-service
-   notification-service
-   whatsapp-service
-   audit-service
-   reporting-service
-   admin-ui

## Database (High Level)

Core tables: - tenant - tenant_user - customer - category - product -
inventory - inventory_transaction - order_header - order_item -
order_status_history - audit_log - whatsapp_message - notification -
language - configuration

Every business table includes: - tenant_id - created_at - updated_at -
created_by - updated_by - version

## Order Lifecycle

NEW → CONFIRMED → ACCEPTED → PICKING → PACKED → DISPATCHED → DELIVERED →
CANCELLED

## Audit

Capture: - old value - new value - user - timestamp - IP - channel (WEB
/ WHATSAPP / API)

## UI

Vendor: - Dashboard - Products - Inventory - Orders - Customers -
Reports - Audit - Settings

Customer: - WhatsApp conversational interface - Optional web portal

## Technology Stack

-   Java 21
-   Spring Boot
-   Spring Security
-   Spring Data JPA
-   PostgreSQL
-   Thymeleaf
-   Maven
-   Flyway
-   Redis
-   Docker
-   Nginx
-   GitHub Actions

## API Examples

-   POST /api/orders
-   GET /api/orders/{id}
-   GET /api/products
-   POST /api/inventory
-   GET /api/customers

## Deployment

-   Docker Compose
-   PostgreSQL
-   Redis
-   Spring Boot
-   Nginx
-   HTTPS
-   Railway / AWS / Azure / GCP

## Future Enhancements

-   Payments
-   Barcode scanning
-   QR ordering
-   AI demand forecasting
-   Purchase orders
-   Suppliers
-   Warehouse support
-   Mobile app
-   Offline sync

## Development Roadmap

Phase 1: Authentication, tenants, products, inventory, WhatsApp
ordering.

Phase 2: Vendor UI, reports, audit, notifications.

Phase 3: Analytics, AI, mobile app, integrations.

## Recommendation

Use Domain Driven Design, layered architecture, DTOs, MapStruct, Bean
Validation, centralized exception handling, idempotent order APIs, and
event-driven notifications.

This document is the blueprint for a production-grade SaaS inventory
platform.
