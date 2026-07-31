import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Sheet, SheetContent, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Separator } from "@/components/ui/separator";
import { ProductCard, ProductType } from "@/components/mesa/ProductCard";
import { ProductDetailsDialog } from "@/components/mesa/ProductDetailsDialog";
import { CartItem } from "@/components/mesa/CartItem";
import Header from "@/components/Header";
import Footer from "@/components/Footer";
import { useDeliveryCatalog } from "@/hooks/useDeliveryCatalog";
import { useDeliveryCart } from "@/hooks/useDeliveryCart";
import { useDeliveryCheckout } from "@/hooks/useDeliveryCheckout";
import { DeliveryCheckoutSection } from "@/components/delivery/DeliveryCheckoutSection";
import { DeliveryCatalogFilters } from "@/components/delivery/DeliveryCatalogFilters";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Languages } from "lucide-react";
import { useDeliveryI18n } from "@/i18n/useDeliveryI18n";
import { useMesaI18n } from "@/i18n/useMesaI18n";

type LocaleOption = {
  value: "pt-BR" | "en-US" | "es-ES";
  label: string;
};

export default function DeliveryMenuPage() {
  const [error, setError] = useState("");
  const { t, formatCurrency, locale, setLocale } = useDeliveryI18n();
  const { t: tMesa, setLocale: setLocaleMesa } = useMesaI18n("delivery");
  const catalog = useDeliveryCatalog(locale);
  const cartState = useDeliveryCart();
  const checkout = useDeliveryCheckout({ cart: cartState.cart, total: cartState.total, onError: setError, locale });
  const navigate = useNavigate();
  const [detailsOpen, setDetailsOpen] = useState(false);
  const [detailProduct, setDetailProduct] = useState<ProductType | null>(null);
  const cartSectionRef = useRef<HTMLDivElement | null>(null);
  const [cartSectionVisible, setCartSectionVisible] = useState(false);
  const paymentSectionRef = useRef<HTMLDivElement | null>(null);
  const localeOptions: LocaleOption[] = [
    { label: "PT", value: "pt-BR" },
    { label: "EN", value: "en-US" },
    { label: "ES", value: "es-ES" },
  ];
  const handleLocaleChange = (value: string) => {
    if (value === locale) return;
    setLocale(value as LocaleOption["value"]);
    setLocaleMesa(value as LocaleOption["value"]);
  };

  useEffect(() => {
    setLocaleMesa(locale as LocaleOption["value"]);
  }, [locale, setLocaleMesa]);

  const catalogErrorMessage = catalog.errorKey ? t(catalog.errorKey) : catalog.error;
  const pageError = catalogErrorMessage || error;
  const showCartBar = !checkout.checkoutOpen && cartState.cart.length > 0 && !cartSectionVisible;

  useEffect(() => {
    const element = cartSectionRef.current;
    if (!element) return;
    const observer = new IntersectionObserver(
      (entries) => {
        const entry = entries[0];
        setCartSectionVisible(entry.isIntersecting);
      },
      { threshold: 0.1 }
    );
    observer.observe(element);
    return () => observer.disconnect();
  }, [cartState.cart.length]);

  useEffect(() => {
    if (!checkout.checkoutOpen) return;
    requestAnimationFrame(() => {
      paymentSectionRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
    });
  }, [checkout.checkoutOpen]);

  const openDetails = (product: ProductType) => {
    setDetailProduct(product);
    setDetailsOpen(true);
  };

  const handleGoToAreaCliente = () => {
    checkout.setPaymentSuccessOpen(false);
    navigate("/areacliente", { replace: true });
  };

  return (
    <div className="min-h-screen bg-soft-white flex flex-col">
      <Header />
      <main className="flex-1">
        <div className="max-w-6xl mx-auto p-4 sm:p-6 space-y-4">
          <header className="flex flex-col gap-2">
              <div>
                <div className="flex flex-wrap items-center gap-3 justify-between">
                  <h1 className="text-2xl font-semibold">{t("delivery.menu.title")}</h1>
                  <Select value={locale} onValueChange={handleLocaleChange}>
                    <SelectTrigger
                      className="h-7 py-0.5 px-3 text-[11px] font-medium border border-accent/30 bg-white/80 text-foreground hover:bg-accent/10 shadow-sm self-start rounded-full w-fit min-w-[60px]"
                      aria-label={t("delivery.menu.localeSelectorAria")}
                    >
                      <Languages className="h-3.5 w-3.5 mr-1" />
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {localeOptions.map((option) => (
                        <SelectItem key={option.value} value={option.value}>
                          {option.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                {checkout.checkoutOpen ? (
                  <div className="space-y-2">
                    <p className="text-sm text-muted-foreground">{t("delivery.menu.description.checkoutOpen")}</p>
                    <Button variant="outline" size="sm" onClick={checkout.handleBackToCart}>
                      {t("delivery.menu.button.backToCart")}
                    </Button>
                  </div>
                ) : (
                  <p className="text-sm text-muted-foreground">{t("delivery.menu.description.idle")}</p>
                )}
              </div>
            {!checkout.checkoutOpen && (
              <DeliveryCatalogFilters
                search={catalog.search}
                onSearchChange={(value) => catalog.setSearch(value)}
                categories={catalog.categories}
                selectedCategoryId={catalog.selectedCategoryId}
                onCategoryChange={(categoryId) => catalog.setSelectedCategoryId(categoryId)}
                t={t}
              />
            )}
          </header>

          {pageError && <div className="p-3 rounded bg-red-100 text-red-700 text-sm">{pageError}</div>}
          {catalog.loading && <div className="text-sm text-muted-foreground">{t("delivery.menu.loading")}</div>}

          <div className="grid gap-4 lg:grid-cols-[2fr_1fr]">
            <div className="space-y-3">
              {!checkout.checkoutOpen &&
                (catalog.allProducts.length === 0 && !catalog.loading ? (
                  <Card className="p-4 text-sm text-muted-foreground">{t("delivery.menu.emptyCatalog")}</Card>
                ) : (
                  catalog.allProducts.map((product) => (
                      <ProductCard
                        key={`${product.id}-${(product as any).skuId ?? "base"}`}
                        product={product}
                        onOpenDetails={openDetails}
                        onAdd={cartState.handleAddDefault}
                        onAddSku={cartState.handleAddSku}
                        disabled={false}
                        hasPairingsHint={false}
                        t={tMesa}
                      />
                    ))
                  ))}
            </div>

            <aside className="space-y-3">
              {!checkout.checkoutOpen && (
                <Card className="p-4 space-y-3" ref={cartSectionRef}>
                  <div className="flex items-center justify-between">
                    <h2 className="text-lg font-semibold">{t("delivery.menu.cart.title")}</h2>
                    <span className="text-sm text-muted-foreground">
                      {t("delivery.menu.cart.itemsCount", { count: cartState.cart.length })}
                    </span>
                  </div>
                  <Separator />
                  {cartState.cart.length === 0 ? (
                    <div className="text-sm text-muted-foreground">{t("delivery.menu.cart.empty")}</div>
                  ) : (
                    <div className="space-y-3">
                      {cartState.cart.map((item, index) => (
                      <CartItem
                        key={`${item.produtoId}-${item.skuId ?? "base"}-${index}`}
                        item={item}
                        id={index}
                        onRemove={cartState.handleRemove}
                        onIncrease={cartState.handleIncrease}
                        onDecrease={cartState.handleDecrease}
                        onChangeObservacao={cartState.handleChangeObs}
                        t={tMesa}
                      />
                    ))}
                      <Separator />
                        <div className="flex items-center justify-between text-sm">
                          <span>{t("delivery.menu.cart.totalLabel")}</span>
                          <span className="text-base font-semibold">
                            {formatCurrency(cartState.total, { fromCents: false })}
                          </span>
                        </div>
                      <Button
                        className="w-full"
                        size="lg"
                        disabled={cartState.cart.length === 0 || checkout.processing}
                        onClick={() => checkout.setOrderConfirmOpen(true)}
                      >
                        {checkout.processing
                          ? t("delivery.menu.cart.button.processing")
                          : checkout.orderId
                          ? t("delivery.menu.cart.button.update")
                          : t("delivery.menu.cart.button.send")}
                      </Button>
                    </div>
                  )}
                </Card>
              )}

              {checkout.checkoutOpen ? (
                <DeliveryCheckoutSection
                  paymentSectionRef={paymentSectionRef}
                  total={cartState.total}
                  checkout={checkout}
                  cartHasItems={cartState.cart.length > 0}
                  cartItems={cartState.cart}
                  locale={locale}
                />
            ) : (
                <Card className="p-4 text-sm text-muted-foreground">
                  {t("delivery.menu.emptyState.description")}
                </Card>
              )}
            </aside>
          </div>

          <ProductDetailsDialog
            open={detailsOpen}
            product={detailProduct}
            onOpenChange={setDetailsOpen}
            onAdd={cartState.handleAddDefault}
            onAddSku={cartState.handleAddSku}
            pairings={[]}
            t={tMesa}
          />

          {showCartBar && (
            <div className="fixed bottom-0 inset-x-0 z-40 bg-white border-t shadow-[0_-4px_6px_-1px_rgba(0,0,0,0.1)] border-border/40 pb-[env(safe-area-inset-bottom)]">
              <div className="max-w-md mx-auto px-3 py-2 flex items-center justify-between gap-2">
                <div className="min-w-0 text-xs sm:text-sm text-muted-foreground whitespace-nowrap">
                  <span className="font-medium">
                    {t("delivery.menu.cart.bar.items", {
                      count: cartState.cart.reduce((sum, item) => sum + item.quantidade, 0),
                    })}
                  </span>
                  <span className="mx-2 text-muted-foreground/60">•</span>
                  <span className="font-medium">
                    {formatCurrency(cartState.total, { fromCents: false })}
                  </span>
                </div>
                <div className="flex items-center gap-2">
                  <Button variant="outline" size="sm" onClick={() => checkout.setCartOpen(true)}>
                    {t("delivery.menu.cart.bar.button.view")}
                  </Button>
                  <Button size="sm" onClick={() => checkout.setOrderConfirmOpen(true)} disabled={checkout.processing}>
                    {checkout.processing
                      ? t("delivery.menu.cart.button.processing")
                      : t("delivery.menu.cart.bar.button.send")}
                  </Button>
                </div>
              </div>
            </div>
          )}

          <Sheet open={checkout.cartOpen} onOpenChange={checkout.setCartOpen}>
            <SheetContent side="bottom" className="p-0">
              <div className="max-w-md mx-auto w-full">
                <SheetHeader className="px-4 py-3 border-b">
                  <SheetTitle className="text-sm">{t("delivery.menu.cart.sheet.title")}</SheetTitle>
                </SheetHeader>
                <div className="p-4">
                  {cartState.cart.length === 0 ? (
                    <div className="text-sm text-muted-foreground">{t("delivery.menu.cart.sheet.empty")}</div>
                  ) : (
                    <div className="space-y-3">
                      {cartState.cart.map((item, index) => (
                        <CartItem
                          key={`${item.produtoId}-${item.skuId ?? "base"}-${index}`}
                          item={item}
                          id={index}
                          onRemove={cartState.handleRemove}
                          onIncrease={cartState.handleIncrease}
                          onDecrease={cartState.handleDecrease}
                          onChangeObservacao={cartState.handleChangeObs}
                          t={tMesa}
                        />
                      ))}
                      <Separator />
                      <div className="flex items-center justify-between text-sm">
                        <span>{t("delivery.menu.cart.sheet.totalLabel")}</span>
                        <span className="text-base font-semibold">
                          {formatCurrency(cartState.total, { fromCents: false })}
                        </span>
                      </div>
                      <Button
                        className="w-full"
                        disabled={checkout.processing}
                        onClick={() => checkout.setOrderConfirmOpen(true)}
                      >
                        {checkout.processing
                          ? t("delivery.menu.cart.sheet.button.processing")
                          : t("delivery.menu.cart.sheet.button.send")}
                      </Button>
                    </div>
                  )}
                </div>
              </div>
            </SheetContent>
          </Sheet>

          <AlertDialog open={checkout.orderConfirmOpen} onOpenChange={checkout.setOrderConfirmOpen}>
            <AlertDialogContent>
              <AlertDialogHeader>
                <AlertDialogTitle>
                  {checkout.orderId
                    ? t("delivery.menu.dialog.orderConfirmation.title.update", { orderId: checkout.orderId })
                    : t("delivery.menu.dialog.orderConfirmation.title.create")}
                </AlertDialogTitle>
                <AlertDialogDescription>
                  {checkout.orderId
                    ? t("delivery.menu.dialog.orderConfirmation.description.update")
                    : t("delivery.menu.dialog.orderConfirmation.description.create")}
                </AlertDialogDescription>
              </AlertDialogHeader>
              <div className="space-y-3">
                {cartState.cart.length === 0 ? (
                  <div className="text-sm text-muted-foreground">
                    {t("delivery.menu.dialog.orderConfirmation.empty")}
                  </div>
                ) : (
                  <div className="space-y-2 text-sm">
                    {cartState.cart.map((item, index) => (
                      <div
                        key={`${item.produtoId}-${item.skuId ?? "base"}-${index}`}
                        className="flex items-center justify-between"
                      >
                        <div className="min-w-0">
                          <div className="font-medium truncate">{item.nome}</div>
                          <div className="text-xs text-muted-foreground">
                            {t("delivery.menu.dialog.orderConfirmation.quantityLabel", {
                              quantity: item.quantidade,
                            })}
                          </div>
                        </div>
                        <div className="text-sm font-medium">
                          {formatCurrency(item.preco * item.quantidade, { fromCents: false })}
                        </div>
                      </div>
                    ))}
                    <Separator />
                    <div className="flex items-center justify-between text-sm font-medium">
                      <span>{t("delivery.menu.dialog.orderConfirmation.totalLabel")}</span>
                      <span>{formatCurrency(cartState.total, { fromCents: false })}</span>
                    </div>
                  </div>
                )}
              </div>
              <AlertDialogFooter>
                <AlertDialogCancel>{t("delivery.menu.dialog.orderConfirmation.back")}</AlertDialogCancel>
                <AlertDialogAction
                  onClick={checkout.handleConfirmOrder}
                  disabled={checkout.processing || cartState.cart.length === 0}
                >
                  {checkout.processing
                    ? t("delivery.menu.cart.button.processing")
                    : checkout.orderId
                    ? t("delivery.menu.dialog.orderConfirmation.submit.update")
                    : t("delivery.menu.dialog.orderConfirmation.submit.create")}
                </AlertDialogAction>
              </AlertDialogFooter>
            </AlertDialogContent>
          </AlertDialog>

          <Dialog open={checkout.paymentSuccessOpen} onOpenChange={checkout.setPaymentSuccessOpen}>
            <DialogContent className="sm:max-w-md bg-gradient-to-br from-[hsl(var(--accent)/0.08)] via-white to-[hsl(var(--accent)/0.12)] border-[hsl(var(--accent)/0.25)] shadow-xl">
              <DialogHeader>
                <DialogTitle className="text-xl font-semibold text-foreground flex items-center gap-2">
                  {t("delivery.menu.dialog.paymentSuccess.title")}
                </DialogTitle>
                <DialogDescription className="text-sm text-muted-foreground">
                  {t("delivery.menu.dialog.paymentSuccess.description")}
                </DialogDescription>
              </DialogHeader>
              <div className="text-sm text-muted-foreground">
                {checkout.orderId ? (
                  <div className="rounded-lg border border-[hsl(var(--accent)/0.2)] bg-white px-3 py-2 text-foreground">
                    <div className="text-xs uppercase tracking-widest text-muted-foreground/80">
                      {t("delivery.menu.dialog.paymentSuccess.orderLabel")}
                    </div>
                    <div className="font-semibold text-lg">#{checkout.orderId}</div>
                  </div>
                ) : null}
              </div>
              <DialogFooter className="flex flex-col sm:flex-row sm:justify-end gap-2">
                <Button variant="ghost" onClick={() => checkout.setPaymentSuccessOpen(false)}>
                  {t("delivery.menu.dialog.paymentSuccess.close")}
                </Button>
                <Button
                  onClick={handleGoToAreaCliente}
                  className="bg-[hsl(var(--accent))] hover:bg-[hsl(var(--accent)/0.9)] text-white"
                >
                  {t("delivery.menu.dialog.paymentSuccess.clientArea")}
                </Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>
        </div>
      </main>
      <Footer />
    </div>
  );
}
