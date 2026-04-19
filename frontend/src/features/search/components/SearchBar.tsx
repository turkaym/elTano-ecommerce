interface SearchBarProps {
  value: string
  onChange: (nextValue: string) => void
  onSubmit?: (value: string) => void
}

export function SearchBar({ value, onChange, onSubmit }: SearchBarProps) {

  return (
    <form
      className="search-bar"
      role="search"
      onSubmit={(event) => {
        event.preventDefault()
        onSubmit?.(value)
      }}
    >
      <label htmlFor="storefront-search" className="search-label">
        Buscar productos
      </label>
      <input
        id="storefront-search"
        type="search"
        placeholder="Buscar frutos secos, harinas, semillas..."
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </form>
  )
}
