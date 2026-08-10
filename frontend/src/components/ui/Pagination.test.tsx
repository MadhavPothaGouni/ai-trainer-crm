import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { Pagination } from "./Pagination";

describe("Pagination", () => {
  it("renders nothing when there are no results", () => {
    const { container } = render(
      <Pagination pageNumber={0} totalPages={0} first totalElements={0} last onPageChange={vi.fn()} />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it("disables Previous on the first page and Next on the last page", () => {
    render(<Pagination pageNumber={0} totalPages={1} first last totalElements={3} onPageChange={vi.fn()} />);
    expect(screen.getByRole("button", { name: "Previous" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Next" })).toBeDisabled();
  });

  it("calls onPageChange with the next page number", async () => {
    const onPageChange = vi.fn();
    const user = userEvent.setup();
    render(<Pagination pageNumber={0} totalPages={3} first={true} last={false} totalElements={50} onPageChange={onPageChange} />);

    await user.click(screen.getByRole("button", { name: "Next" }));

    expect(onPageChange).toHaveBeenCalledWith(1);
  });

  it("calls onPageChange with the previous page number", async () => {
    const onPageChange = vi.fn();
    const user = userEvent.setup();
    render(<Pagination pageNumber={1} totalPages={3} first={false} last={false} totalElements={50} onPageChange={onPageChange} />);

    await user.click(screen.getByRole("button", { name: "Previous" }));

    expect(onPageChange).toHaveBeenCalledWith(0);
  });

  it("shows a human-readable summary", () => {
    render(<Pagination pageNumber={1} totalPages={5} first={false} last={false} totalElements={97} onPageChange={vi.fn()} />);
    expect(screen.getByText("Page 2 of 5 · 97 total")).toBeInTheDocument();
  });
});
