# Delivery i18n inventory

This document lists every literal string currently used by the delivery screens and shows the corresponding `delivery.*` translation key that should replace it. Keeping this inventory in sync ensures we cover the entire scope from the feature brief.

## `src/pages/DeliveryMenuPage.tsx`
- Page header: `delivery.menu.title`
- Locale selector (aria label): `delivery.menu.localeSelectorAria`
- Checkout prompt (active cart): `delivery.menu.description.checkoutOpen`
- Idle prompt (no checkout): `delivery.menu.description.idle`
- Empty catalog callout: `delivery.menu.emptyState.description`
- Catalog load failure: `delivery.menu.errors.catalogLoad`
- Cart labels:
  - Title: `delivery.menu.cart.title`
  - Item count text: `delivery.menu.cart.itemsCount`
  - Empty message: `delivery.menu.cart.empty`
  - Total label: `delivery.menu.cart.totalLabel`
  - Primary button text:
    - Processing: `delivery.menu.cart.button.processing`
    - Send: `delivery.menu.cart.button.send`
    - Update: `delivery.menu.cart.button.update`
  - Loading indicator: `delivery.menu.loading`
  - Empty catalog card: `delivery.menu.emptyCatalog`
  - Back-to-cart button: `delivery.menu.button.backToCart`
- Fixed cart bar:
  - Summary text: `delivery.menu.cart.bar.items`, `delivery.menu.cart.bar.total`
  - Buttons: `delivery.menu.cart.bar.button.view`, `delivery.menu.cart.bar.button.send`
- Cart sheet and dialog texts map to: `delivery.menu.cart.sheet.*` and `delivery.menu.dialog.orderConfirmation.*`, `delivery.menu.dialog.paymentSuccess.*`.

## `src/pages/MenuPage.tsx`
- Hero title: `delivery.menuPage.hero.title`
- Hero subtitle: `delivery.menuPage.hero.description`
- Signature badge: `delivery.menu.signature`
- Static content warning: `delivery.menu.errorStaticContent`
## `src/components/Header.tsx`
- Navigation links: `delivery.header.nav.home`, `delivery.header.nav.about`, `delivery.header.nav.events`, `delivery.header.nav.gallery`, `delivery.header.nav.contact`, `delivery.header.nav.menu`
- Active table button: `delivery.header.activeTable`
- User dropdown: `delivery.header.dropdown.*`
- Login/Reservation buttons: `delivery.header.login`, `delivery.header.reserveTable`
- Mobile menu aria label: `delivery.header.mobileMenu`
- Locale selector aria label: `delivery.header.localeSelectorAria`

## `src/components/delivery/DeliveryCatalogFilters.tsx`
- Search placeholder: `delivery.filters.search.placeholder`
- “Todas” button text: `delivery.filters.category.all`

## `src/components/delivery/DeliveryCheckoutSection.tsx`
- Summary card:
  - Created label: `delivery.checkout.card.summary.created`
  - Order number label: `delivery.checkout.card.summary.orderLabel`
  - Items total label: `delivery.checkout.card.summary.itemsTotal`
  - Mode label/description: `delivery.checkout.card.summary.modeLabel`, `delivery.checkout.card.summary.modeDescription.*`
- Accordion titles and service mode text: `delivery.checkout.section.dataTitle.*`, `delivery.checkout.section.serviceMode`, `delivery.checkout.serviceMode.*`
- Form placeholders and informational text: `delivery.checkout.form.*`, `delivery.checkout.form.pickupInfo`
- Summary & buttons: `delivery.checkout.summary.*`
- Payment tab:
  - Title: `delivery.payment.title`
  - Total label: `delivery.payment.totalWithFeeLabel`
  - Method buttons: `delivery.payment.method.card`, `delivery.payment.method.pix`
  - PIX instructions/buttons/labels: `delivery.payment.pix.*`
  - Card field labels/placeholders: `delivery.payment.card.*`
  - Installments label: `delivery.payment.card.installments.label` and detail strings
  - Pay button: `delivery.payment.card.button.pay`, `delivery.payment.card.button.processing`
  - Status info: `delivery.payment.statusLabel`, `delivery.payment.info.*`
  - Status labels: `delivery.statusLabels.*`
  - Toast title: `delivery.toast.paymentFailed.title` + dynamic description
- Toast for pending payment uses `delivery.payment.pix.instructions.existing`

## `src/hooks/useDeliveryCheckout.ts`
- Error/friendly messages map to keys under `delivery.errors.*`
- PIX polling failure uses `delivery.errors.pixPolling`
- Toast failure uses `delivery.toast.paymentFailed.*`
- Friendly message override for installments uses `delivery.errors.installmentsCountry`

## `src/pages/DeliveryOrderConfirmationPage.tsx`
- Banner texts: `delivery.orderConfirmation.banner.*`
- CTA buttons: `delivery.orderConfirmation.cta.*`
- Summary card labels: `delivery.orderConfirmation.summary.*`
- Customer card titles: `delivery.orderConfirmation.customer.title`
- Location card titles/info: `delivery.orderConfirmation.location.*`
- Items section labels/messages: `delivery.orderConfirmation.items.*`

Each of these keys already exists in `src/i18n/delivery.{pt-BR,en-US,es-ES}.json`. Any uncovered literal should be added here and to the translation bundles before replacing the text in the JSX/TS files.
