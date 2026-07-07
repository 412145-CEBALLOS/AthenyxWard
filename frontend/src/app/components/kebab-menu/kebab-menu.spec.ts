import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { KebabMenuComponent, MenuItem } from './kebab-menu';

const makeItems = (overrides: Partial<MenuItem>[] = []): MenuItem[] =>
  overrides.map((o) => ({
    id: 'test',
    label: 'Test item',
    ...o,
  }));

describe('KebabMenuComponent', () => {
  let component: KebabMenuComponent;
  let fixture: ComponentFixture<KebabMenuComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [KebabMenuComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(KebabMenuComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  function setItems(items: MenuItem[]): void {
    fixture.componentRef.setInput('items', items);
    fixture.detectChanges();
  }

  function openKebab(): void {
    fixture.nativeElement.querySelector('.kebab-trigger').click();
    fixture.detectChanges();
  }

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('renders the trigger button with default aria-label', () => {
    const btn = fixture.nativeElement.querySelector('.kebab-trigger');
    expect(btn).toBeTruthy();
    expect(btn.getAttribute('aria-label')).toBe('Más acciones');
  });

  it('renders 5 items when 5 items are provided', () => {
    setItems([
      { id: 'a', label: 'Item A' },
      { id: 'b', label: 'Item B' },
      { id: 'c', label: 'Item C' },
      { id: 'd', label: 'Item D' },
      { id: 'e', label: 'Item E' },
    ]);
    openKebab();
    const items = fixture.nativeElement.querySelectorAll('[role="menuitem"]');
    expect(items.length).toBe(5);
  });

  it('opens dropdown on trigger click and closes on second click', () => {
    setItems([{ id: 'a', label: 'Item A' }]);
    const btn = fixture.nativeElement.querySelector('.kebab-trigger');
    btn.click();
    fixture.detectChanges();
    expect(component.isOpen()).toBeTrue();
    expect(fixture.nativeElement.querySelector('.kebab-dropdown')).toBeTruthy();
    btn.click();
    fixture.detectChanges();
    expect(component.isOpen()).toBeFalse();
    expect(fixture.nativeElement.querySelector('.kebab-dropdown')).toBeFalsy();
  });

  it('closes dropdown when clicking outside', () => {
    setItems([{ id: 'a', label: 'Item A' }]);
    openKebab();
    expect(component.isOpen()).toBeTrue();
    document.body.click();
    fixture.detectChanges();
    expect(component.isOpen()).toBeFalse();
  });

  it('closes dropdown on Escape keydown', () => {
    setItems([{ id: 'a', label: 'Item A' }]);
    openKebab();
    expect(component.isOpen()).toBeTrue();
    const event = new KeyboardEvent('keydown', { key: 'Escape' });
    document.dispatchEvent(event);
    fixture.detectChanges();
    expect(component.isOpen()).toBeFalse();
  });

  it('emits action with item id when clicking an enabled item', () => {
    setItems([{ id: 'my-action', label: 'My Action' }]);
    openKebab();
    let emitted: string | null = null;
    component.action.subscribe((id) => (emitted = id));
    fixture.nativeElement.querySelector('[role="menuitem"]').click();
    expect(emitted as unknown as string).toBe('my-action');
  });

  it('does NOT emit when clicking a disabled item', () => {
    setItems([{ id: 'disabled-action', label: 'Disabled', disabled: true }]);
    openKebab();
    let emitted: string | null = null;
    component.action.subscribe((id) => (emitted = id));
    fixture.nativeElement.querySelector('[role="menuitem"]').click();
    expect(emitted).toBeNull();
  });

  it('disabled item has aria-disabled="true"', () => {
    setItems([{ id: 'x', label: 'X', disabled: true }]);
    openKebab();
    const item = fixture.nativeElement.querySelector('[role="menuitem"]');
    expect(item.getAttribute('aria-disabled')).toBe('true');
  });

  it('disabled item shows tooltip via title attribute', () => {
    setItems([{ id: 'x', label: 'X', disabled: true, disabledTooltip: 'Premium only' }]);
    openKebab();
    const item = fixture.nativeElement.querySelector('[role="menuitem"]');
    expect(item.getAttribute('title')).toBe('Premium only');
  });

  it('destructive item gets kebab-item-destructive class', () => {
    setItems([{ id: 'del', label: 'Delete', variant: 'destructive' }]);
    openKebab();
    const item = fixture.nativeElement.querySelector('.kebab-item');
    expect(item.classList).toContain('kebab-item-destructive');
  });

  it('active item gets kebab-item-active class', () => {
    setItems([{ id: 'star', label: 'Mark important', active: true }]);
    openKebab();
    const item = fixture.nativeElement.querySelector('.kebab-item');
    expect(item.classList).toContain('kebab-item-active');
  });

  it('trigger has aria-haspopup="menu" and aria-expanded', () => {
    setItems([{ id: 'a', label: 'A' }]);
    const btn = fixture.nativeElement.querySelector('.kebab-trigger');
    expect(btn.getAttribute('aria-haspopup')).toBe('menu');
    expect(btn.getAttribute('aria-expanded')).toBe('false');
    openKebab();
    expect(btn.getAttribute('aria-expanded')).toBe('true');
  });

  it('dropdown has role="menu"', () => {
    setItems([{ id: 'a', label: 'A' }]);
    openKebab();
    expect(fixture.nativeElement.querySelector('[role="menu"]')).toBeTruthy();
  });

  it('each item has role="menuitem"', () => {
    setItems([{ id: 'a', label: 'A' }, { id: 'b', label: 'B' }]);
    openKebab();
    const items = fixture.nativeElement.querySelectorAll('[role="menuitem"]');
    expect(items.length).toBe(2);
  });

  it('skips disabled items on ArrowDown navigation', () => {
    setItems([
      { id: 'a', label: 'A', disabled: true },
      { id: 'b', label: 'B' },
      { id: 'c', label: 'C' },
    ]);
    openKebab();
    const menu = fixture.nativeElement.querySelector('[role="menu"]');
    const event = (key: string) =>
      new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true });

    menu.dispatchEvent(event('ArrowDown'));
    fixture.detectChanges();
    expect(component.isActive(1)).toBeTrue();
  });

  it('Home goes to first enabled item', () => {
    setItems([
      { id: 'a', label: 'A', disabled: true },
      { id: 'b', label: 'B' },
      { id: 'c', label: 'C' },
    ]);
    openKebab();
    tick();
    const menu = fixture.nativeElement.querySelector('[role="menu"]');
    menu.dispatchEvent(new KeyboardEvent('keydown', { key: 'Home', bubbles: true }));
    fixture.detectChanges();
    expect(component.isActive(1)).toBeTrue();
  });

  it('End goes to last enabled item', () => {
    setItems([
      { id: 'a', label: 'A' },
      { id: 'b', label: 'B', disabled: true },
      { id: 'c', label: 'C' },
    ]);
    openKebab();
    tick();
    const menu = fixture.nativeElement.querySelector('[role="menu"]');
    menu.dispatchEvent(new KeyboardEvent('keydown', { key: 'End', bubbles: true }));
    tick();
    fixture.detectChanges();
    expect(component.isActive(2)).toBeTrue();
  });

  it('Enter on focused item emits its id', fakeAsync(() => {
    setItems([{ id: 'enter-action', label: 'Enter Me' }]);
    openKebab();
    tick();
    let emitted: string | null = null;
    component.action.subscribe((id) => (emitted = id));
    const menu = fixture.nativeElement.querySelector('[role="menu"]');
    menu.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    tick();
    expect(emitted as unknown as string).toBe('enter-action');
  }));

  it('Space on focused item emits its id', fakeAsync(() => {
    setItems([{ id: 'space-action', label: 'Space Me' }]);
    openKebab();
    tick();
    let emitted: string | null = null;
    component.action.subscribe((id) => (emitted = id));
    const menu = fixture.nativeElement.querySelector('[role="menu"]');
    menu.dispatchEvent(new KeyboardEvent('keydown', { key: ' ', bubbles: true }));
    tick();
    expect(emitted as unknown as string).toBe('space-action');
  }));
});
