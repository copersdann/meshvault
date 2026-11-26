from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Response, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from ..db import get_db
from ..models import Printer, utcnow
from ..schemas import (
    PrinterCreate,
    PrinterDiscoveryRead,
    PrinterProbe,
    PrinterRead,
    PrinterStatusRead,
    PrinterUpdate,
    ProbeResult,
)
from ..serializers import printer_read
from ..services.discovery import discover_printers
from ..services.moonraker import MoonrakerClient, MoonrakerError, normalize_host, parse_status
from ..services.security import decrypt_secret, encrypt_secret, require_auth

router = APIRouter(
    prefix="/api",
    tags=["printers"],
    dependencies=[Depends(require_auth)],
)


def _client(printer: Printer) -> MoonrakerClient:
    return MoonrakerClient(printer.host, decrypt_secret(printer.api_key_enc))


def _get_printer(db: Session, printer_id: str) -> Printer:
    printer = db.get(Printer, printer_id)
    if printer is None:
        raise HTTPException(status_code=404, detail="Printer not found")
    return printer


def _moonraker_error(exc: MoonrakerError) -> HTTPException:
    return HTTPException(status_code=502, detail=str(exc))


@router.get("/printers/discover", response_model=list[PrinterDiscoveryRead])
def discover() -> list[PrinterDiscoveryRead]:
    return [PrinterDiscoveryRead(**item) for item in discover_printers()]


@router.get("/printers", response_model=list[PrinterRead])
def list_printers(db: Session = Depends(get_db)) -> list[PrinterRead]:
    printers = db.scalars(select(Printer).order_by(Printer.name.asc())).all()
    return [printer_read(printer) for printer in printers]


@router.post(
    "/printers",
    response_model=PrinterRead,
    status_code=status.HTTP_201_CREATED,
)
def create_printer(
    payload: PrinterCreate,
    db: Session = Depends(get_db),
) -> PrinterRead:
    try:
        host = normalize_host(payload.host)
    except MoonrakerError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    printer = Printer(
        name=payload.name.strip(),
        host=host,
        kind=payload.kind,
        api_key_enc=encrypt_secret(payload.api_key),
        profile_json=payload.profile_json.model_dump(),
        enabled=payload.enabled,
    )
    db.add(printer)
    db.commit()
    return printer_read(printer)


@router.patch("/printers/{printer_id}", response_model=PrinterRead)
def update_printer(
    printer_id: str,
    payload: PrinterUpdate,
    db: Session = Depends(get_db),
) -> PrinterRead:
    printer = _get_printer(db, printer_id)
    if payload.name is not None:
        printer.name = payload.name.strip()
    if payload.host is not None:
        try:
            printer.host = normalize_host(payload.host)
        except MoonrakerError as exc:
            raise HTTPException(status_code=400, detail=str(exc)) from exc
    if payload.api_key is not None:
        printer.api_key_enc = encrypt_secret(payload.api_key)
    if payload.profile_json is not None:
        printer.profile_json = payload.profile_json.model_dump()
    if payload.enabled is not None:
        printer.enabled = payload.enabled
    printer.updated_at = utcnow()
    db.commit()
    return printer_read(printer)


@router.delete("/printers/{printer_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_printer(printer_id: str, db: Session = Depends(get_db)) -> Response:
    printer = _get_printer(db, printer_id)
    db.delete(printer)
    db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.post("/printers/probe", response_model=ProbeResult)
async def probe_printer(payload: PrinterProbe) -> ProbeResult:
    try:
        client = MoonrakerClient(payload.host, payload.api_key)
        info = await client.server_info()
        result = info.get("result", {})
        return ProbeResult(
            ok=True,
            klippy_state=result.get("klippy_state"),
            message="Connection successful",
        )
    except MoonrakerError as exc:
        return ProbeResult(ok=False, message=str(exc))


@router.post("/printers/{printer_id}/test", response_model=ProbeResult)
async def test_printer(
    printer_id: str,
    db: Session = Depends(get_db),
) -> ProbeResult:
    printer = _get_printer(db, printer_id)
    try:
        info = await _client(printer).server_info()
        result = info.get("result", {})
        return ProbeResult(
            ok=True,
            klippy_state=result.get("klippy_state"),
            message="Connection successful",
        )
    except MoonrakerError as exc:
        return ProbeResult(ok=False, message=str(exc))


@router.get("/printers/{printer_id}/status", response_model=PrinterStatusRead)
async def printer_status(
    printer_id: str,
    db: Session = Depends(get_db),
) -> PrinterStatusRead:
    printer = _get_printer(db, printer_id)
    try:
        payload = await _client(printer).status()
    except MoonrakerError as exc:
        raise _moonraker_error(exc) from exc
    return PrinterStatusRead.model_validate(parse_status(payload))


@router.post("/printers/{printer_id}/pause", response_model=PrinterStatusRead)
async def pause_printer(
    printer_id: str,
    db: Session = Depends(get_db),
) -> PrinterStatusRead:
    printer = _get_printer(db, printer_id)
    try:
        client = _client(printer)
        await client.pause()
        return PrinterStatusRead.model_validate(parse_status(await client.status()))
    except MoonrakerError as exc:
        raise _moonraker_error(exc) from exc


@router.post("/printers/{printer_id}/resume", response_model=PrinterStatusRead)
async def resume_printer(
    printer_id: str,
    db: Session = Depends(get_db),
) -> PrinterStatusRead:
    printer = _get_printer(db, printer_id)
    try:
        client = _client(printer)
        await client.resume()
        return PrinterStatusRead.model_validate(parse_status(await client.status()))
    except MoonrakerError as exc:
        raise _moonraker_error(exc) from exc


@router.post("/printers/{printer_id}/cancel", response_model=PrinterStatusRead)
async def cancel_printer(
    printer_id: str,
    db: Session = Depends(get_db),
) -> PrinterStatusRead:
    printer = _get_printer(db, printer_id)
    try:
        client = _client(printer)
        await client.cancel()
        return PrinterStatusRead.model_validate(parse_status(await client.status()))
    except MoonrakerError as exc:
        raise _moonraker_error(exc) from exc
