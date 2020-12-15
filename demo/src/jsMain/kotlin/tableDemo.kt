import dev.fritz2.binding.RootStore
import dev.fritz2.binding.storeOf
import dev.fritz2.components.*
import dev.fritz2.dom.html.Div
import dev.fritz2.dom.html.RenderContext
import dev.fritz2.identification.uniqueId
import dev.fritz2.lenses.Lens
import dev.fritz2.lenses.buildLens
import dev.fritz2.lenses.format
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import model.Address
import model.Person
import kotlin.random.Random

val largeFakePersonSet = """
Rudolf Tintzmann-Gumprich;2009-01-20;bogdan59@seifert.de;+49 (0) 9791 516599;02326 19959;Heidi-Jüttner-Gasse;0/6;18257;Dinkelsbühl
Astrid Wirth-Rudolph;1989-11-07;bjuncken@aol.de;05650 75063;(03755) 848404;Ludmila-Löffler-Weg;3/5;08350;Bad Kreuznach
Prof. Amalie Wulff;2015-03-16;pwirth@waehner.com;0579186932;09559142510;Dörschnerstraße;1;02013;Herford
Philip Bohnbach;2001-03-13;qholzapfel@yahoo.de;+49(0)6284 12182;(03107) 80584;Löfflerring;020;51148;Nabburg
Dipl.-Ing. Kirstin Baum;2012-09-03;uwagenknecht@jungfer.net;06077 457492;+49(0)3060 993882;Milica-Drewes-Allee;64;14330;Saarbrücken
Prof. Iwan auch Schlauchin B.Sc.;2004-02-05;fweiss@stroh.com;03887 586498;+49 (0) 9915 034433;Hartungstr.;560;74791;Meppen
Prof. Adem Fritsch;2020-09-12;drubinester@segebahn.de;(05765) 37299;07850 995248;Bert-Schomber-Gasse;7;95222;Sangerhausen
Stefano Seidel;1994-10-25;segebahnelke@yahoo.de;00529 753918;(00251) 88701;August-Weller-Allee;18;83363;Vechta
Univ.Prof. Mirja Döring B.Eng.;2010-06-13;liobazaenker@yahoo.de;09414 82509;0744716952;Heserstr.;07;00618;Füssen
Giuseppe Killer;1983-08-18;natalie08@riehl.com;+49(0)4289 523639;+49(0) 111782812;Isa-Pärtzelt-Platz;76;62943;Sankt Goar
""".trimIndent()

val smallFakePersonSet = """
Prof. Witold Bonbach;1984-04-06;eigenwilligkristin@flantz.de;(04646) 28143;02164 478473;Hansjürgen-Holt-Platz;98;73075;Bautzen
Claus-Dieter Kohl;2004-09-09;bolandergerhard@googlemail.com;0294300781;02685 561820;Sinaida-Siering-Gasse;180;34359;Konstanz
Mesut Hellwig;2012-06-12;leonhard18@schmiedt.com;(07910) 43746;00725 598286;Hedda-Rudolph-Weg;190;30114;Stendal
Frau Betty Junitz;2005-12-20;xknappe@aol.de;+49(0)4952 731322;(05632) 988292;Heßplatz;31;27085;Stade
Maike Wohlgemut;2017-06-09;groettnerstanislaus@nette.de;09598 094041;05378416242;Petar-Hartung-Allee;1/2;93583;Duderstadt
Erika Bolnbach-Bolnbach;2017-08-06;atzleriwona@web.de;0807034829;(07241) 001911;Kathi-Häring-Gasse;6/3;61654;Wurzen
Yvette Gröttner B.Sc.;1976-02-08;amies@stiebitz.de;+49(0)5060 233940;+49(0)2684182702;Krokergasse;701;05497;Hainichen
""".trimIndent()

fun parseCsvPersons(fakeData: String) = fakeData.split('\n').map {
    val fields = it.split(';')
    Person(
        uniqueId(),
        Random.nextInt(1, 100),
        fields[0],
        LocalDate.parse(fields[1]),
        fields[2],
        fields[3],
        fields[4],
        Address(
            fields[5],
            fields[6],
            fields[7],
            fields[8],
        )
    )
}

val fakeData = mapOf(false to parseCsvPersons(largeFakePersonSet), true to parseCsvPersons(smallFakePersonSet))


val _idLens = buildLens("_id", Person::_id) { p, v -> p.copy(_id = v) }
val personIdLens = buildLens("id", Person::id) { p, v -> p.copy(id = v) }
val fullNameLens = buildLens("fullName", Person::fullName) { p, v -> p.copy(fullName = v) }
val birthdayLens = buildLens("birthday", Person::birthday) { p, v -> p.copy(birthday = v) }
val emailLens = buildLens("email", Person::email) { p, v -> p.copy(email = v) }
val mobileLens = buildLens("mobile", Person::mobile) { p, v -> p.copy(mobile = v) }
val phoneLens = buildLens("phone", Person::phone) { p, v -> p.copy(phone = v) }


val personAddressLens = buildLens("address", Person::address) { p, v -> p.copy(address = v) }
val streetLens = buildLens("street", Address::street) { p, v -> p.copy(street = v) }
val houseNumberLens = buildLens("houseNumber", Address::houseNumber) { p, v -> p.copy(houseNumber = v) }
val postalCodeLens = buildLens("postalCode", Address::postalCode) { p, v -> p.copy(postalCode = v) }
val cityLens = buildLens("city", Address::city) { p, v -> p.copy(city = v) }


object TableStore : RootStore<List<Person>>(fakeData[false]!!, "personData") {
    val remove = handle<Person> { list, toDelete ->
        list.filter { it != toDelete }
    }
}

val toggle = storeOf(false)

object Formats {
    val intFormat: Lens<Int, String> = format(
        parse = { it.toInt() },
        format = { it.toString() }
    )

    val dateFormat: Lens<LocalDate, String> = format(
        parse = { LocalDate.parse(it) },
        format = {
            "${it.dayOfMonth.toString().padStart(2, '0')}.${
                it.monthNumber.toString().padStart(2, '0')
            }.${it.year}"
        }
    )
}


@ExperimentalCoroutinesApi
fun RenderContext.tableDemo(): Div {
    toggle.data.map { fakeData[it]!! } handledBy TableStore.update

    return contentFrame {
        showcaseHeader("Tables")

        val selectedStore = object : RootStore<List<Person>>(emptyList()) {

            val add = handle<Person> { list, selected ->
                if (!list.contains(selected)) {
                    list + selected
                } else {
                    list
                }
            }

            val toggle = handle<Person> { list, item ->
                console.info(item)
                if (!list.contains(item)) {
                    list + item
                } else {
                    list.filter { it != item }
                }
            }

            val clearList = handle {
                emptyList()
            }

        }

        val selectionModeStore = storeOf(TableComponent.Companion.SelectionMode.NONE)
        componentFrame {
            stackUp {
                items {
                    clickButton { text("toggle data") }.map { !toggle.current } handledBy toggle.update
                    lineUp {
                        items {
                            TableComponent.Companion.SelectionMode.values().map { mode ->
                                clickButton { text(mode.toString()) }.events.map { mode } handledBy selectionModeStore.update
                            }
                        }
                    }
                }
            }
        }

        selectedStore.data.render { list ->
            paragraph { +"Aktuell sind ${list.size} Zeilen ausgewählt!" }
        }


        selectionModeStore.data.render {
            table(rowIdProvider = Person::id) {
                caption(selectionModeStore.data.map { mode ->
                    "Table with \"${mode.name}\" Selection Mode "
                })
                tableStore(TableStore)
                selectedRows(selectedStore.data)
                selectedAllRowEvents = selectedStore.update
                selectedRowEvent = selectedStore.toggle
                selectionMode(selectionModeStore.current)
                defaultMinWidth = "250px"

                defaultThStyle {
                    {
                        background { color { "#1fd257" } }
                    }
                }

                columns {
                    column("ID") {
                        lens { personIdLens + Formats.intFormat }
                        width { minmax { "80px" } }
                    }
                    column("Name") {
                        lens { fullNameLens }
                        width { minmax { "2fr" } }
                    }
                    column("Birthday") {
                        lens { birthdayLens + Formats.dateFormat }
                        width { minmax { "120px" } }
                        styling {
                            color { danger }
                        }
                        sortBy {
                            compareBy { person ->
                                person.birthday
                            }
                        }
                    }
                    column {
                        // lens can be omitted! It's purely optional and totally ok to have columns that hide its relation to
                        // the data from the table itself!
                        // ``header`` oder ``head``?
                        header {
                            title { "Address" }
                            styling {
                                background { color { "purple" } }
                                fontWeight { bold }
                            }
                            content { config ->
                                +config.headerName
                                icon { fromTheme { fritz2 } }
                            }
                        }
                        width { max { "4fr" } }
                        content { ctx, _, rowStore ->
                            rowStore?.let { person ->
                                val street = person.sub(personAddressLens + streetLens)
                                val houseNumber = person.sub(personAddressLens + houseNumberLens)
                                val postalCode = person.sub(personAddressLens + postalCodeLens)
                                val city = person.sub(personAddressLens + cityLens)
                                ctx.apply {
                                    street.data.combine(houseNumber.data) { s, h ->
                                        "$s $h"
                                    }.combine(postalCode.data) { a, p ->
                                        "$a ,$p"
                                    }.combine(city.data) { a, c ->
                                        "$a $c"
                                    }.asText()
                                }

                            }
                        }
                        sortBy {
                            compareBy<Person> { person ->
                                person.address.city
                            }.thenBy { person ->
                                person.address.street
                            }
                        }
                    }
                    column("Phone") {
                        lens { phoneLens }
                        // TODO: Ugly -> Enum must be receiver; but how?
                        sorting { TableComponent.Companion.Sorting.DISABLED }
                    }
                    column("Mobile") { lens { mobileLens } }
                    column("E-Mail") { lens { emailLens } }
                }
                sorter = NaiveSorter()
            }
        }
    }
}